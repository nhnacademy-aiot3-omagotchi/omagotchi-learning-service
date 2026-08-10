package site.omagotchi.learningservice.occupancy.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.attendance.application.AttendancePresenceQueryService;
import site.omagotchi.learningservice.attendance.application.result.OpenPresenceView;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.occupancy.application.event.RoomVacatedEvent;
import site.omagotchi.learningservice.occupancy.application.port.OccupancyEventPublisher;
import site.omagotchi.learningservice.occupancy.application.port.OccupancyParticipantRepository;
import site.omagotchi.learningservice.occupancy.application.port.RoomOccupancyRepository;
import site.omagotchi.learningservice.occupancy.application.port.SpaceReader;
import site.omagotchi.learningservice.occupancy.application.result.RoomOccupancyResult;
import site.omagotchi.learningservice.occupancy.domain.OccupancyParticipant;
import site.omagotchi.learningservice.occupancy.domain.RoomOccupancy;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
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
    private final AttendancePresenceQueryService attendancePresenceQueryService;
    private final RoomOccupancyRepository occupancyRepository;
    private final OccupancyParticipantRepository participantRepository;
    private final OccupancyEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * 점유를 시작한다.
     *
     * @param spaceId 대상 회의실
     * @param userId  요청자 계정 id. 점유자 멤버십은 재실 구간에서 도출하므로 받지 않는다
     * @throws BusinessException 회의실 아님·비활성(400), 재실 아님(403), 공간 없음(404),
     *                           사용 중·이미 점유 중·이미 참여 중(409)
     * @throws RuntimeException  출결 모듈 조회 자체가 실패한 경우. 잡지 않고 전파해
     *                           {@code GlobalExceptionHandler}가 500으로 옮긴다
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

        OpenPresenceView presence = findOpenPresence(userId);                      // MR-22

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
        //
        // 이 방의 정리는 공실 알림을 발행하지 않는다. 지금 이 요청이 곧바로 점유할
        // 방이라, 발행하면 대기자들이 "비었다"는 알림을 받고 와서 409를 맞는다.
        // 공실 알림은 일회성 의사표시라(notified_at 소진) 헛된 알림 하나가
        // 그 사람의 신청을 태워 없앤다.
        closeParticipants(occupancyRepository.expireStaleBySpaceId(spaceId, now));

        // 반면 계정 기준 정리는 다른 방이다. 그 방은 실제로 비었으므로 발행해야 한다 —
        // 여기서 EXPIRED로 바꿔버리면 스케줄러가 다시 찾지 못해 알림이 영영 유실된다.
        vacate(occupancyRepository.expireStaleByUserId(userId, now));

        // 선검사. 통과해도 안전이 보장되지는 않는다 — 동시 요청은 둘 다 "없음"을 볼 수 있고,
        // 그때는 부분 유니크가 최종 방어선이다. 여기서 거르는 이유는 흔한 경로에서
        // 예외 스택을 태우지 않기 위해서다.
        if (occupancyRepository.existsActiveBySpaceId(spaceId, now)) {
            throw new BusinessException(OccupancyErrorCode.ROOM_ALREADY_OCCUPIED);  // MR-09
        }
        if (occupancyRepository.existsActiveByUserId(userId, now)) {
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
     * 재실 조회. "재실 아님"만 403으로 변환한다.
     *
     * <p>조회 자체가 실패하면(출결 모듈 장애 등) 여기서 잡지 않고 그대로 전파한다 —
     * 클라이언트가 분기할 외부 계약이 없는 기술 실패이므로 {@code BusinessException}으로
     * 숨기지 않는다 (04-error-handling "예상하지 못한 실패"). 결과적으로 안전 측 차단은
     * 유지되면서(요청 실패), {@code GlobalExceptionHandler}가 스택 트레이스를 한 번
     * 남기고 500으로 옮긴다.</p>
     */
    private OpenPresenceView findOpenPresence(UUID userId) {
        return attendancePresenceQueryService.findOpenPresence(userId)
                .orElseThrow(() -> new BusinessException(OccupancyErrorCode.NOT_PRESENT));
    }

    /**
     * 만료 정리로 끝난 점유의 참여자를 함께 마감한다 (MR-32).
     *
     * <p>점유 행만 EXPIRED로 바꾸고 여기서 멈추면 <b>그 참여자들이 다시는 어떤 회의에도
     * 들어갈 수 없다.</b> {@code uq_occupancy_participants_one_active}가 계정 기준이라
     * 열린 참여 행 하나가 그 계정을 영구히 묶는다. 점유 시작이 스스로를 참여자로
     * 등록하므로(MR-27) 새 점유도 같이 막힌다 — 만료된 방의 점유자가 다른 방을 잡으려 하면
     * {@code OCCUPANCY_ALREADY_PARTICIPATING}(409)을 받는다.</p>
     *
     * <p>마감 시각이 정리 시각이 아니라 점유의 종료 시각인 것이 요점이다. 실제로 회의가
     * 끝난 것은 {@code expires_at}이고, 정리는 그 뒤에 도착한 다른 요청이 대행할 뿐이라
     * 지금 시각을 찍으면 참여 시간이 실제보다 길게 집계된다.</p>
     *
     * <p>반복 호출은 안전하다 — {@code closeAllActiveByOccupancyId}가
     * {@code WHERE left_at IS NULL} 조건부 UPDATE라 이미 닫힌 행을 덮어쓰지 않는다.
     * 공간 기준 정리와 계정 기준 정리가 같은 점유를 가리킬 수 있어 필요한 성질이다.</p>
     */
    private void closeParticipants(List<RoomOccupancyRepository.ExpiredOccupancy> expired) {
        expired.forEach(occupancy -> participantRepository
                .closeAllActiveByOccupancyId(occupancy.occupancyId(), occupancy.endedAt()));
    }

    /**
     * 참여자를 마감하고 공실을 알린다.
     *
     * <p>{@link #closeParticipants}와 나눠 둔 이유는 "정리했다"와 "비었다"가 항상 같지
     * 않기 때문이다. 지금 점유하려는 방을 정리한 것은 사용자에게 공실이 아니며, 그때
     * 알리면 일회성 신청이 헛되이 소진된다.</p>
     *
     * <p>{@code vacatedAt}이 {@code now}가 아닌 것은 발송이 늦어도 "언제 비었는지"의
     * 정본이 종료 시각이기 때문이다 ({@link RoomVacatedEvent} 참고).</p>
     */
    private void vacate(List<RoomOccupancyRepository.ExpiredOccupancy> expired) {
        closeParticipants(expired);
        expired.forEach(occupancy -> eventPublisher.publishRoomVacated(new RoomVacatedEvent(
                occupancy.spaceId(), occupancy.occupancyId(), occupancy.endedAt())));
    }
}