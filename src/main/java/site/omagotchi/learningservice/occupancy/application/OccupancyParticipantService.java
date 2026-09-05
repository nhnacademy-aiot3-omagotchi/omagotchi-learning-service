package site.omagotchi.learningservice.occupancy.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.attendance.application.AttendancePresenceQueryService;
import site.omagotchi.learningservice.attendance.application.result.OpenPresenceView;
import site.omagotchi.learningservice.cohort.application.CohortLockService;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.cohort.application.result.CohortMembershipView;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.occupancy.application.port.OccupancyParticipantRepository;
import site.omagotchi.learningservice.occupancy.application.port.RoomOccupancyRepository;
import site.omagotchi.learningservice.space.application.SpaceAccessService;
import site.omagotchi.learningservice.space.application.result.SpaceAccessView;
import site.omagotchi.learningservice.occupancy.domain.OccupancyParticipant;
import site.omagotchi.learningservice.occupancy.domain.RoomOccupancy;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * 점유 세션의 참여자 관리 (MR-19, MR-28, MR-29, MR-30, MR-31, MR-33).
 *
 * <p><b>세 연산 모두 활성 점유 행 락으로 시작한다.</b> 정원 검증 때문만이 아니다 —
 * 락 없이 처리하면 종료된 점유에 {@code left_at IS NULL} 행이 남고,
 * {@code uq_occupancy_participants_one_active}가 계정 기준이라 그 사용자가 영구히
 * 다른 회의에 참여할 수 없게 된다.</p>
 *
 * <p>참여자 추가의 락 순서는 cohort_memberships → room_occupancies → attendance_records다.
 * {@code spaces}는 정원을 읽기만 하고 락을 잡지 않는다 — 정원이 바뀌어도 이미 들어온
 * 참여자를 쫓아내지는 않기 때문이다.</p>
 *
 * <p>기수 정합(MR-33)이 이 클래스의 핵심 책임이다. 스키마 v1.3에서 {@code cohort_id}
 * 컬럼과 복합 FK를 제거했으므로 DB가 "참여자의 기수 = 점유자의 기수"를 보장하지 않는다.
 * 여기 검증이 유일한 방어선이다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OccupancyParticipantService {

    private final SpaceAccessService spaceAccessService;
    private final AttendancePresenceQueryService attendancePresenceQueryService;
    private final CohortMembershipQueryService cohortMembershipQueryService;
    private final CohortLockService cohortLockService;
    private final RoomOccupancyRepository occupancyRepository;
    private final OccupancyParticipantRepository participantRepository;
    private final MeetingPresenceCoordinator meetingPresenceCoordinator;
    private final Clock clock;

    /**
     * 참여자를 추가한다 (MR-19, MR-28, MR-29, MR-30, MR-33). 수락·거절 절차는 없다.
     *
     * <p>이미 이 회의에 참여 중인 사용자를 다시 추가하면 아무것도 바꾸지 않고 성공으로
     * 끝난다(멱등). 좌석을 새로 쓰지 않으므로 정원 검사도 거치지 않는다 — 그러지 않으면
     * 정원이 찼을 때만 실패하는, 잔여 좌석에 따라 결과가 갈리는 요청이 된다.</p>
     *
     * @param spaceId         대상 회의실
     * @param targetUserId    추가할 사용자
     * @param requesterUserId 요청자. 점유자 본인이어야 한다
     * @throws BusinessException 타 기수 대상(400), 요청자가 점유자 아님·대상 비재실(403),
     *                           공간 없음(404), 종료된 점유·정원 초과(409) —
     *                           <b>만료 시각이 지났으나 아직 ACTIVE인 점유도 종료로 본다</b>,
     *                           대상이 <b>다른</b> 회의에 참여 중(409, MR-30 —
     *                           {@code uq_occupancy_participants_one_active} 위반이
     *                           {@code ALREADY_PARTICIPATING}으로 변환된다)
     */
    @Transactional
    public void add(Long spaceId, UUID targetUserId, UUID requesterUserId) {

        // ── 락 밖: 검증과 외부 조회 ────────────────────────────────
        // 엔티티가 아니라 값으로 읽는다. 여기서 RoomOccupancy를 읽으면 1차 캐시에 올라가
        // 아래 lockById()의 상태 재확인이 락 이전 스냅샷을 보게 된다.
        RoomOccupancyRepository.ActiveOccupancy occupancy = findActiveOccupancy(spaceId);
        requireOccupier(occupancy, requesterUserId);

        SpaceAccessView room = spaceAccessService.find(spaceId)
                .orElseThrow(() -> new BusinessException(OccupancyErrorCode.SPACE_NOT_FOUND));

        // 점유자 본인의 멤버십이 이미 비활성이면 대상과 무관하게 점유 자체가 유효하지
        // 않은 상태다. DIFFERENT_COHORT로 던지면 원인이 대상에게 있다고 오인시키므로
        // 전용 코드로 구분한다.
        Long occupierCohortId = cohortIdOf(occupancy.occupierMembershipId(),
                OccupancyErrorCode.OCCUPIER_MEMBERSHIP_INACTIVE);

        // 대상의 멤버십은 열린 재실 구간에서 온다 (MR-19). 점유 시작과 같은 규약이며,
        // 요청 본문에 기수나 멤버십을 받지 않는 이유도 같다.
        OpenPresenceView presence = attendancePresenceQueryService.findOpenPresence(targetUserId)
                .orElseThrow(() -> new BusinessException(OccupancyErrorCode.TARGET_NOT_PRESENT));

        // MR-33. 재실 구간에서 온 "실제로 저장할 멤버십"을 잠근 뒤 기수를 비교한다.
        // 대상 계정이 점유자 기수에 소속돼 있는지만 보면, 다기수 담당자가 다른 기수
        // 멤버십으로 출근한 경우 검증을 통과한 기수와 저장되는 기수가 어긋난다.
        CohortMembershipView lockedTargetMembership = cohortLockService
                .lockActiveMembership(presence.cohortMembershipId())
                .orElseThrow(() -> new BusinessException(
                        OccupancyErrorCode.TARGET_MEMBERSHIP_NOT_ACTIVE));
        if (!occupierCohortId.equals(lockedTargetMembership.cohortId())) {
            throw new BusinessException(OccupancyErrorCode.DIFFERENT_COHORT);
        }

        // ── 락 구간 ──────────────────────────────────────────────
        RoomOccupancy locked = lockActive(occupancy.id());
        OffsetDateTime now = OffsetDateTime.now(clock);

        // 만료 시각이 지난 점유는 스케줄러(#9)가 아직 쓸어가지 않았을 뿐 이미 끝난 회의다.
        // isActive()만 보고 넣으면 joined_at이 expires_at보다 뒤인 행이 생기고, 그 뒤
        // 만료 처리가 left_at을 expires_at으로 찍는 순간 ck_occupancy_participants_period를
        // 위반한다 — 그 트랜잭션이 통째로 롤백되므로 점유는 영영 EXPIRED가 되지 못하고
        // 주기마다 같은 실패를 반복하며, 참여자들은 열린 행에 묶여 다른 회의에 못 들어간다.
        if (locked.isExpiredAt(now)) {
            throw new BusinessException(OccupancyErrorCode.OCCUPANCY_ENDED);
        }

        // 조회가 정원 검사보다 먼저인 것이 중요하다. countActiveByOccupancyId는 대상이
        // 이미 활성 참여자면 그 사람까지 세므로, 좌석을 하나도 더 쓰지 않는 요청이
        // 정원 검사에 걸린다. 그러면 같은 요청의 결과가 잔여 좌석이라는 무관한 상태에
        // 따라 갈리고, 클라이언트는 자리를 비워도 해결되지 않는 409를 받는다.
        Optional<OccupancyParticipant> existing =
                participantRepository.findByOccupancyIdAndUserId(locked.getId(), targetUserId);

        // 이미 참여 중이면 좌석을 새로 쓰지 않는다. 다만 참여 행만 남고 MEETING 구간이
        // 유실된 부분 실패를 자가 복구하기 위해 체류 상태는 다시 맞춘다. 같은 회의에
        // 이미 들어가 있으면 coordinator가 아무것도 하지 않으므로 멱등이다.
        Optional<OccupancyParticipant> activeParticipant =
                existing.filter(OccupancyParticipant::isActive);
        if (activeParticipant.isPresent()) {
            meetingPresenceCoordinator.ensureEntered(
                    activeParticipant.get().getCohortMembershipId(),
                    spaceId,
                    now
            );
            return;
        }

        // 좌석을 새로 소비하는 요청만 정원을 검사한다 (MR-28).
        // 락 안에서 세는 것도 중요하다. 락 밖에서 세면 잔여 1석에 둘이 동시에 들어와
        // 정원을 넘는다 — "최대 N행"은 유니크 인덱스로 표현할 수 없어 이 카운트가
        // 유일한 방어선이다.
        if (participantRepository.countActiveByOccupancyId(locked.getId()) >= room.capacity()) {
            throw new BusinessException(OccupancyErrorCode.CAPACITY_EXCEEDED);
        }

        // 재합류는 새 행이 아니라 기존 행의 left_at 복원이다 (결정 #30).
        // uq_occupancy_participants_pair가 점유당 사람 1행이라 INSERT는 애초에 들어가지 않고,
        // 행을 지웠다 넣으면 참여 이력이 사라진다.
        existing.ifPresentOrElse(
                OccupancyParticipant::rejoin,
                () -> participantRepository.save(OccupancyParticipant.join(
                        locked.getId(),
                        presence.cohortMembershipId(),
                        targetUserId,
                        now
                ))
        );
        meetingPresenceCoordinator.enter(presence, spaceId, now);
    }

    /**
     * 참여자를 내보낸다 — 본인 이탈과 점유자의 제외를 함께 처리한다 (MR-31).
     *
     * <p>두 흐름을 한 메서드로 둔 이유는 결과가 완전히 같기 때문이다. 조건부로
     * {@code left_at}을 찍는 동작에 차이가 없고, 나뉘면 락 순서와 종료 점유 판정이
     * 두 곳에 중복된다. 다른 것은 권한 판정 한 줄뿐이다.</p>
     *
     * @param targetUserId 내보낼 사용자. 요청자 본인이면 이탈, 아니면 제외다
     * @throws BusinessException 점유자를 제외 시도(400), 권한 없음(403),
     *                           참여자 아님(404), 종료된 점유(409)
     */
    @Transactional
    public void remove(Long spaceId, UUID targetUserId, UUID requesterUserId) {

        RoomOccupancyRepository.ActiveOccupancy occupancy = findActiveOccupancy(spaceId);

        // 본인 이탈이거나 점유자의 제외여야 한다.
        boolean self = targetUserId.equals(requesterUserId);
        if (!self && !requesterUserId.equals(occupancy.occupierUserId())) {
            throw new BusinessException(OccupancyErrorCode.NOT_OCCUPIER);
        }

        // 점유자는 이 경로로 나갈 수 없다 — 스스로도, 남에 의해서도.
        // 점유자가 빠지면 주인 없는 활성 점유가 남으므로 반납으로만 종료해야 한다.
        if (targetUserId.equals(occupancy.occupierUserId())) {
            throw new BusinessException(OccupancyErrorCode.OCCUPIER_CANNOT_LEAVE);
        }

        RoomOccupancy locked = lockActive(occupancy.id());

        OccupancyParticipant participant = participantRepository
                .findByOccupancyIdAndUserId(locked.getId(), targetUserId)
                .orElseThrow(() -> new BusinessException(OccupancyErrorCode.PARTICIPANT_NOT_FOUND));

        // 이미 나간 사람에게 다시 요청이 와도 성공으로 둔다. 결과 상태가 같으므로
        // 재시도·중복 클릭에 409를 주면 클라이언트가 처리할 것이 없다.
        if (!participant.isActive()) {
            log.debug("이미 이탈한 참여자입니다. occupancyId={}", locked.getId());
            return;
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        meetingPresenceCoordinator.leaveOne(participant, spaceId, now);
    }

    // ────────────────────────────── 내부 헬퍼 ──────────────────────────────

    /** 락 밖 사전 검증용 값 조회. 활성 점유가 없으면 "이미 종료된 점유"(409)다. */
    private RoomOccupancyRepository.ActiveOccupancy findActiveOccupancy(Long spaceId) {
        return occupancyRepository.findActiveSummaryBySpaceId(spaceId)
                .orElseThrow(() -> new BusinessException(OccupancyErrorCode.OCCUPANCY_ENDED));
    }

    private void requireOccupier(RoomOccupancyRepository.ActiveOccupancy occupancy, UUID userId) {
        if (!occupancy.occupierUserId().equals(userId)) {
            throw new BusinessException(OccupancyErrorCode.NOT_OCCUPIER);
        }
    }

    /**
     * 점유 행을 락으로 잡고 아직 활성인지 재확인한다.
     *
     * <p>활성 조건을 락 쿼리에 넣지 않는 것이 의도다. 락 획득 후 확인해야 "스케줄러가
     * EXPIRED로 바꾼 직후 도착한 요청"을 409로 정확히 잡는다.</p>
     */
    private RoomOccupancy lockActive(Long occupancyId) {
        RoomOccupancy locked = occupancyRepository.lockById(occupancyId)
                .orElseThrow(() -> new BusinessException(OccupancyErrorCode.OCCUPANCY_ENDED));
        if (!locked.isActive()) {
            throw new BusinessException(OccupancyErrorCode.OCCUPANCY_ENDED);
        }
        return locked;
    }

    /**
     * 멤버십에서 기수를 되찾는다.
     *
     * <p>점유 행이 {@code cohort_id}를 갖지 않으므로(ERD v3) 기수 파트에 되묻는다.
     * ACTIVE 멤버십이 아니면 기수를 특정할 수 없어 정합 검증이 성립하지 않는다.
     * 어느 쪽 멤버십을 찾다가 실패했는지에 따라 원인이 다르므로(점유자 자신의 상태 vs
     * 대상과의 기수 불일치), 호출부가 상황에 맞는 코드를 지정한다.</p>
     */
    private Long cohortIdOf(Long membershipId, OccupancyErrorCode notFoundCode) {
        return cohortMembershipQueryService.findActiveMembership(membershipId)
                .map(CohortMembershipView::cohortId)
                .orElseThrow(() -> new BusinessException(notFoundCode));
    }
}
