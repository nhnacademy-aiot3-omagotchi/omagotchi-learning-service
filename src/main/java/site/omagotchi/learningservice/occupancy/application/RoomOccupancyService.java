package site.omagotchi.learningservice.occupancy.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.occupancy.application.port.OccupancyParticipantRepository;
import site.omagotchi.learningservice.occupancy.application.port.PresenceReader;
import site.omagotchi.learningservice.occupancy.application.port.RoomOccupancyRepository;
import site.omagotchi.learningservice.occupancy.application.port.SpaceReader;
import site.omagotchi.learningservice.occupancy.application.result.RoomOccupancyResult;
import site.omagotchi.learningservice.occupancy.domain.OccupancyParticipant;
import site.omagotchi.learningservice.occupancy.domain.RoomOccupancy;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 회의실 점유 시작 (MR-01, 08, 09, 10, 11, 20, 22, 27, 35 / RM-13).
 *
 * <p><b>락 순서는 항상 spaces → room_occupancies 로 고정한다.</b> 연장·반납(#7)과
 * 참여자 추가(#6)도 같은 순서를 따라야 하며, 어기면 데드락이 난다.</p>
 *
 * <p>검증을 락 밖에서 먼저 끝내고 락 구간에는 확인과 INSERT만 남긴다 —
 * 재실 조회는 출결 모듈 호출이라 락 안에 넣으면 그 시간만큼 같은 회의실의 다른
 * 요청이 전부 대기한다 ({@code TeamMemberService.addMember}와 같은 구조).</p>
 *
 * <p>기수 컨텍스트를 요청받지 않는다. 점유는 재실이 전제 조건이므로(MR-22)
 * 열린 재실 구간의 {@code cohort_membership_id}를 그대로 점유자 멤버십으로 쓴다.
 * 요청 본문에 기수 식별자를 추가하면 안 된다 — 출근한 기수와 다른 기수로 점유하는
 * 경로가 열린다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomOccupancyService {

    private final SpaceReader spaceReader;
    private final PresenceReader presenceReader;
    private final RoomOccupancyRepository occupancyRepository;
    private final OccupancyParticipantRepository participantRepository;
    private final Clock clock;

    /**
     * 점유를 시작한다.
     *
     * @param spaceId 대상 회의실
     * @param userId  요청자 계정 id. 점유자 멤버십은 재실 구간에서 도출하므로 받지 않는다
     * @throws BusinessException 회의실 아님·비활성(400), 재실 아님(403), 공간 없음(404),
     *                           사용 중·이미 점유 중·이미 참여 중(409), 출결 조회 불가(503)
     */
    @Transactional
    public RoomOccupancyResult start(Long spaceId, UUID userId) {

        // ── 락 밖: 검증과 외부 조회 ────────────────────────────────
        // 엔티티가 아니라 값으로 읽는다. SpaceJpaEntity를 여기서 읽으면 1차 캐시에 올라가
        // 아래 lock()의 상태 재확인이 락 이전 스냅샷을 보게 된다.
        SpaceReader.MeetingRoom room = spaceReader.find(spaceId)
                .orElseThrow(() -> new BusinessException(OccupancyErrorCode.SPACE_NOT_FOUND));

        if (!room.meetingRoom()) {
            throw new BusinessException(OccupancyErrorCode.NOT_MEETING_ROOM);      // MR-20
        }
        if (!room.active()) {
            throw new BusinessException(OccupancyErrorCode.SPACE_INACTIVE);        // RM-13
        }

        PresenceReader.PresenceContext presence = findOpenPresence(userId);        // MR-22

        // ── 락 구간 ──────────────────────────────────────────────
        // 활성 조건을 쿼리에 넣지 않고 락 획득 후 확인한다. 그래야 "비활성화 커밋 직후
        // 도착한 요청"을 404가 아니라 400으로 정확히 잡는다.
        SpaceReader.MeetingRoom locked = spaceReader.lock(spaceId)
                .orElseThrow(() -> new BusinessException(OccupancyErrorCode.SPACE_NOT_FOUND));
        if (!locked.active()) {
            throw new BusinessException(OccupancyErrorCode.SPACE_INACTIVE);
        }

        OffsetDateTime now = OffsetDateTime.now(clock);

        // 만료됐지만 스케줄러(#9)가 아직 안 쓸어간 행을 먼저 정리한다.
        // 유니크 인덱스는 status만 보고 expires_at은 보지 않으므로, 이 정리가 없으면
        // "목록에는 사용 가능인데 점유하면 409"인 상태가 남는다.
        occupancyRepository.expireStaleBySpaceId(spaceId, now);
        occupancyRepository.expireStaleByUserId(userId, now);

        // 선검사. 통과해도 안전이 보장되지는 않는다 — 동시 요청은 둘 다 "없음"을 볼 수 있고,
        // 그때는 부분 유니크가 최종 방어선이다. 여기서 거르는 이유는 흔한 경로에서
        // 예외 스택을 태우지 않기 위해서다.
        if (occupancyRepository.existsActiveBySpaceId(spaceId)) {
            throw new BusinessException(OccupancyErrorCode.ROOM_ALREADY_OCCUPIED);  // MR-09
        }
        if (occupancyRepository.existsActiveByUserId(userId)) {
            throw new BusinessException(OccupancyErrorCode.ALREADY_OCCUPYING);      // MR-10
        }

        // started_at과 expires_at을 같은 now에서 만든다. DB DEFAULT에 맡기면
        // 두 시각이 어긋나 "expires_at = started_at + 2시간"이 정확히 성립하지 않는다.
        RoomOccupancy occupancy = occupancyRepository.save(RoomOccupancy.start(
                spaceId,
                presence.cohortMembershipId(),
                userId,
                now,
                now.plus(RoomOccupancy.DEFAULT_DURATION)
        ));

        // 점유자를 참여자로 자동 등록한다 (MR-27). 정원 카운트의 기준점이자,
        // uq_occupancy_participants_one_active 덕분에 "다른 회의에 참여 중인 사람의
        // 점유 요청"(MR-30)을 막는 검사이기도 하다. 같은 트랜잭션이므로 여기서 409가 나면
        // 위 점유 INSERT도 함께 롤백된다.
        participantRepository.save(OccupancyParticipant.join(
                occupancy.getId(),
                presence.cohortMembershipId(),
                userId,
                now
        ));

        return RoomOccupancyResult.of(occupancy, now);
    }

    /**
     * 재실 조회. 실패와 "재실 아님"을 구분한다.
     *
     * <p>조회 자체가 실패했을 때 통과시키면 안 된다 — 재실 여부를 확인할 수 없으면
     * 안전 측으로 차단하되, 영구 실패가 아님을 503으로 알린다.</p>
     */
    private PresenceReader.PresenceContext findOpenPresence(UUID userId) {
        try {
            return presenceReader.findOpenPresence(userId)
                    .orElseThrow(() -> new BusinessException(OccupancyErrorCode.NOT_PRESENT));
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("재실 조회 실패. userId={}", userId, exception);
            throw new BusinessException(OccupancyErrorCode.PRESENCE_UNAVAILABLE);
        }
    }
}