package site.omagotchi.learningservice.occupancy.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.cohort.application.result.CohortMembershipView;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.occupancy.application.event.VacancyAlertsDiscardedEvent;
import site.omagotchi.learningservice.occupancy.application.port.OccupancyEventPublisher;
import site.omagotchi.learningservice.occupancy.application.port.RoomOccupancyRepository;
import site.omagotchi.learningservice.occupancy.application.port.VacancyAlertRepository;
import site.omagotchi.learningservice.occupancy.application.result.VacancyAlertView;
import site.omagotchi.learningservice.occupancy.domain.RoomOccupancy;
import site.omagotchi.learningservice.occupancy.domain.VacancyAlert;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 공실 알림 신청·취소·목록 (MR-02, MR-15, MR-17, MR-34).
 *
 * <p>발송(MR-03)은 여기 없다. 이벤트 리스너라 Transaction 성격이 완전히 달라
 * ({@code AFTER_COMMIT} + {@code @Async} + {@code REQUIRES_NEW}) 같은 Class에 두면
 * 자기호출이 Proxy를 우회한다 — {@code OccupancyExpiration}을 나눈 것과 같은 이유다.</p>
 *
 * <p><b>기수 스코프를 두지 않는 것이 의도다</b> (MR-34). 회의실은 여러 기수가 공유하는
 * 자원이라, 타 기수가 점유 중인 방에도 신청할 수 있다. 점유자의 기수를 신청자와 비교하는
 * 검사를 넣으면 공유 자원이 기수별 자원으로 바뀐다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VacancyAlertService {

    private final RoomOccupancyRepository occupancyRepository;
    private final CohortMembershipQueryService cohortMembershipQueryService;
    private final VacancyAlertRepository alertRepository;
    private final OccupancyEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * 공실 알림을 신청한다 (MR-02, MR-15, MR-34).
     *
     * @param cohortId 신청 주체로 쓸 기수. {@code null}이면 활성 소속이 하나일 때만
     *                 그것으로 정한다 — 여럿이면 어느 쪽인지 서버가 고를 수 없다
     * <p><b>점유 행을 잠그고 신청까지 한 Transaction에서 끝낸다.</b> 잠그지 않으면 "조회 →
     * 저장" 사이에 반납이 커밋될 수 있고, 그 반납의 {@code AFTER_COMMIT} 발송이 아직
     * 커밋되지 않은 이 신청을 보지 못한다 — 신청은 <b>400도 발송도 아닌 채 대기로 남아</b>
     * 그 방이 다시 차고 비워질 때까지 기다린다. 사용자는 지금 비어 있는 방을 기다리게 된다.
     * 명세 04 §5가 "먼저 커밋되면 발송 대상, 늦으면 400"이라고 두 갈래로 적은 것은 이
     * 직렬화를 전제한 서술이다.</p>
     *
     * @throws BusinessException 빈 방·본인 방·기수 미지정(400), 활성 소속 없음(403),
     *                           중복 신청(409)
     */
    @Transactional
    public Long request(Long spaceId, Long cohortId, UUID requesterUserId) {

        // 엔티티가 아니라 값으로 읽는다. 여기서 RoomOccupancy를 읽으면 1차 캐시에 올라가
        // 아래 lockById()의 상태 재확인이 락 이전 스냅샷을 보게 된다.
        RoomOccupancyRepository.ActiveOccupancy summary =
                occupancyRepository.findActiveSummaryBySpaceId(spaceId)
                        .orElseThrow(() -> new BusinessException(OccupancyErrorCode.ALERT_ROOM_AVAILABLE));

        // 본인이 쓰고 있는 방을 스스로에게 알릴 이유가 없다. 점유자가 타 기수인 것은
        // 정상이므로(MR-34) 기수가 아니라 계정으로 비교한다.
        if (requesterUserId.equals(summary.occupierUserId())) {
            throw new BusinessException(OccupancyErrorCode.ALERT_OCCUPIER_CANNOT_REQUEST);
        }

        // ── 락 구간 ──────────────────────────────────────────────
        RoomOccupancy locked = occupancyRepository.lockById(summary.id())
                .orElseThrow(() -> new BusinessException(OccupancyErrorCode.ALERT_ROOM_AVAILABLE));
        OffsetDateTime now = OffsetDateTime.now(clock);

        // 잠근 뒤 다시 확인한다. 만료 시각이 지난 행은 스케줄러(#9)가 아직 쓸어가지
        // 않았을 뿐 이미 빈 방이라, 여기서 걸러야 목록의 "사용 중" 판정과 어긋나지 않는다.
        if (!locked.isActive() || locked.isExpiredAt(now)) {
            throw new BusinessException(OccupancyErrorCode.ALERT_ROOM_AVAILABLE);
        }

        Long membershipId = resolveMembershipId(cohortId, requesterUserId);

        // 선검사를 두지 않는다. 대기 중 중복은 uq_vacancy_alerts_waiting이 막고, 그 위반을
        // Persistence가 409로 옮긴다 — select로 먼저 확인해도 동시 요청은 잡지 못하므로
        // 같은 판정이 두 곳에 생길 뿐이다.
        return alertRepository.save(VacancyAlert.request(spaceId, membershipId, now)).getId();
    }

    /**
     * 신청을 취소한다 (MR-17). 행을 물리 삭제한다.
     *
     * <p>이미 발송된 신청과 남의 신청을 같은 404로 돌려준다. 후자를 403으로 구분하면
     * 신청 식별자를 훑어 "이 번호는 존재한다"는 사실이 새어 나간다.</p>
     *
     * <p><b>먼저 읽고 지우지 않는다.</b> 그 사이 발송이 끝나면 삭제가 바뀐 상태를 보지 못해
     * <b>이미 발송된 신청의 이력이 사라진다.</b> 조건을 삭제문에 실으면 삭제가 발송 커밋을
     * 기다린 뒤 {@code notified_at}을 다시 평가해 0행이 되고, 그대로 404가 된다 —
     * 잠금을 따로 잡지 않고도 같은 결과이며, 소속 조회 동안 잠금을 쥐고 있지도 않는다.</p>
     *
     * @throws BusinessException 대기 중 신청이 아니거나 신청자가 아님(404)
     */
    @Transactional
    public void cancel(Long alertId, UUID requesterUserId) {

        // 소유 판정을 멤버십으로 하는 이유는 vacancy_alerts가 user_id를 갖지 않기 때문이다 (v3).
        // 신청 당시의 소속이 이미 끝났다면 여기 없어 지울 수 없는데, 그때는 기수 종료
        // 정리(CE-02)가 대신 지울 대상이라 취소를 허용할 이유가 없다.
        Set<Long> membershipIds = activeMemberships(requesterUserId).stream()
                .map(CohortMembershipView::membershipId)
                .collect(Collectors.toSet());

        if (!alertRepository.deleteWaiting(alertId, membershipIds)) {
            throw new BusinessException(OccupancyErrorCode.ALERT_NOT_FOUND);
        }
    }

    /**
     * 공간 비활성화로 그 공간의 대기 중 신청을 전부 지운다 (RM-15).
     *
     * <p><b>비활성화와 같은 Transaction에서 지운다</b> (명세 04 §2). 나눠서 지우면 비활성
     * 공간에 대기 신청이 남아, 그 방이 다시 활성화될 때까지 아무 일도 일어나지 않는 신청이
     * 된다. {@code space}가 이 Method를 호출하면 {@code REQUIRED}로 합류한다.</p>
     *
     * <p><b>수신자는 삭제 결과에서 나온다</b> ({@code DELETE ... RETURNING}). 물리 삭제
     * 뒤에는 대상을 되찾을 수 없어 이벤트에 실어 보내는데, 삭제와 별도로 미리 읽으면
     * 그 사이 공실 발송이 소진시킨 행이 목록에 남아 <b>방금 공실 알림을 받은 사람에게
     * 취소 통보</b>가 나간다. {@code RoomVacatedEvent}가 "리스너가 조회해 정한다"는 규약을
     * 쓰는 것과 대비된다.</p>
     *
     * <p>통보를 발송할 대상이 없으면 이벤트도 내지 않는다. 빈 목록을 실어 보내면 리스너가
     * 아무에게도 보내지 않을 일을 위해 기수 조회를 한 번 더 한다.</p>
     *
     * @return 지운 건수
     */
    @Transactional
    public int discardBySpace(Long spaceId) {

        // 삭제와 수신자 확보를 한 문장으로 한다 (DELETE ... RETURNING). 먼저 읽고 나중에
        // 지우면 그 사이 공실 발송이 소진시킨 행이 목록에 남아, 방금 공실 알림을 받은
        // 사람에게 "신청이 취소됐다"는 통보가 나간다.
        List<Long> membershipIds = alertRepository.deleteWaitingBySpaceId(spaceId);
        if (membershipIds.isEmpty()) {
            return 0;
        }

        // 커밋 후 비동기로 발송된다 (ADR space-team/0006). 발송 실패는 삭제를 되돌리지
        // 않으며 재시도하지 않는다 — 원천이 이미 없다 (명세 04 §4, at-most-once).
        eventPublisher.publishVacancyAlertsDiscarded(new VacancyAlertsDiscardedEvent(
                spaceId, membershipIds, OffsetDateTime.now(clock)));

        log.info("공간 비활성화로 대기 신청을 삭제했습니다. spaceId={}, 삭제={}건",
                spaceId, membershipIds.size());
        return membershipIds.size();
    }

    /**
     * 내가 신청한 대기 중 목록 (MR-17 1단계).
     *
     * <p>활성 소속 전체를 대상으로 한다. 다기수 담당자가 기수를 바꿔 가며 조회하지 않아도
     * 자기 신청이 한 번에 보여야 취소할 수 있다.</p>
     */
    public List<VacancyAlertView> findMine(UUID requesterUserId) {

        Map<Long, Long> cohortIdByMembershipId = activeMemberships(requesterUserId).stream()
                .collect(Collectors.toMap(
                        CohortMembershipView::membershipId, CohortMembershipView::cohortId));

        if (cohortIdByMembershipId.isEmpty()) {
            return List.of();
        }

        return alertRepository.findWaitingByMembershipIds(cohortIdByMembershipId.keySet()).stream()
                .map(alert -> VacancyAlertView.of(
                        alert, cohortIdByMembershipId.get(alert.getCohortMembershipId())))
                .toList();
    }

    // ────────────────────────────── 내부 헬퍼 ──────────────────────────────

    /**
     * 신청 주체가 될 멤버십을 정한다.
     *
     * <p>기수를 받는 이유는 신청이 계정이 아니라 멤버십 단위이기 때문이다(§4). 다기수
     * 담당자는 같은 방에 기수마다 신청할 수 있어야 하고, 그러려면 어느 소속으로 신청하는지를
     * 서버가 임의로 고르면 안 된다. 소속이 하나뿐인 대다수 사용자에게 매번 기수를 요구하지
     * 않으려고 {@code null}을 허용한다 — {@code SpaceCommandService}의 생성 기수 결정과 같은 규약이다.</p>
     */
    private Long resolveMembershipId(Long cohortId, UUID requesterUserId) {

        List<CohortMembershipView> memberships = activeMemberships(requesterUserId);

        if (memberships.isEmpty()) {
            throw new BusinessException(OccupancyErrorCode.ALERT_COHORT_ACCESS_DENIED);
        }

        if (cohortId == null) {
            if (memberships.size() > 1) {
                throw new BusinessException(OccupancyErrorCode.ALERT_COHORT_ID_REQUIRED);
            }
            return memberships.getFirst().membershipId();
        }

        return memberships.stream()
                .filter(membership -> cohortId.equals(membership.cohortId()))
                .findFirst()
                .map(CohortMembershipView::membershipId)
                .orElseThrow(() -> new BusinessException(OccupancyErrorCode.ALERT_COHORT_ACCESS_DENIED));
    }

    private List<CohortMembershipView> activeMemberships(UUID requesterUserId) {
        return cohortMembershipQueryService.findActiveMemberships(requesterUserId);
    }
}
