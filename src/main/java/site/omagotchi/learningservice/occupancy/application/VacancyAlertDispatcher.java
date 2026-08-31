package site.omagotchi.learningservice.occupancy.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.occupancy.application.port.VacancyAlertRepository;
import site.omagotchi.learningservice.occupancy.application.port.VacancyAlertSender;
import site.omagotchi.learningservice.space.application.SpaceNameQueryService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 회의실 하나가 비었을 때 대기자 전원에게 알린다 (MR-03, MR-04, MR-16, MR-18).
 *
 * <p>신청·취소({@link VacancyAlertService})와 나눈 이유는 Transaction 성격이 다르기
 * 때문이다. 저쪽은 HTTP 요청 하나의 경계 안에서 끝나지만, 여기는 이벤트를 받아 건별로
 * 독립된 Transaction을 연다.</p>
 *
 * <p><b>{@code NOT_SUPPORTED}인 것이 계약이다.</b> 이 Method가 Transaction을 열면 건별
 * {@code REQUIRES_NEW}가 그 안에 중첩되고, 바깥이 길게 열려 있는 동안 잠금이 쌓인다.
 * 여기서 할 일은 후보를 훑는 것뿐이라 열어 둘 이유가 없다 — {@code EndedMembershipSweep}과
 * 같은 규약이다.</p>
 *
 * <p><b>조회는 배치, 발송은 건별이다.</b> 대기자가 N명이어도 후보 조회 1회 + 수신자 조회
 * 1회로 고정된다. 격리(MR-18)를 만드는 것은 조회 위치가 아니라 발송의 Transaction 경계라,
 * 둘은 서로를 방해하지 않는다.</p>
 *
 * <p><b>한 건의 실패가 나머지를 막지 않는다</b> (명세 04 §5 "신청자 5명 중 2명 발송 실패 →
 * 성공 3건만 소진"). 그래서 예외를 건별로 잡아 기록하고 계속한다 — 여기서 전파시키면
 * 뒤쪽 대기자들이 <b>앞사람의 발송 실패 때문에</b> 알림을 못 받는다.</p>
 */
@Slf4j
@Service
public class VacancyAlertDispatcher {

    private final VacancyAlertRepository alertRepository;
    private final CohortMembershipQueryService cohortMembershipQueryService;
    private final SpaceNameQueryService spaceNameQueryService;
    private final VacancyAlertDelivery alertDelivery;
    private final StaleVacancyAlertDiscarder staleAlertDiscarder;

    private final VacancyAlertSender sender;

    /**
     * <b>{@code List}로 받아야 한다.</b> {@code Optional}이나 단건으로 받으면 후보가 둘이어도
     * {@code @Primary} 하나가 모호성을 없애 버려 <b>나머지가 조용히 무시된다.</b>
     * {@code List}만 후보 전부를 보여 준다.
     */
    public VacancyAlertDispatcher(
            VacancyAlertRepository alertRepository,
            CohortMembershipQueryService cohortMembershipQueryService,
            SpaceNameQueryService spaceNameQueryService,
            VacancyAlertDelivery alertDelivery,
            StaleVacancyAlertDiscarder staleAlertDiscarder,
            List<VacancyAlertSender> senders
    ) {
        this.alertRepository = alertRepository;
        this.cohortMembershipQueryService = cohortMembershipQueryService;
        this.spaceNameQueryService = spaceNameQueryService;
        this.alertDelivery = alertDelivery;
        this.staleAlertDiscarder = staleAlertDiscarder;
        // 어느 발송의 성공을 완료로 볼지 정할 수 없는 설정이므로, 방이 비는 순간이 아니라
        // 기동 시점에 멈춘다. 0개도 오류다 — 발송 수단 없이 이 서비스가 할 일은 없다.
        if (senders.size() != 1) {
            throw new IllegalStateException("공실 알림 sender는 정확히 하나여야 합니다: " + senders);
        }
        this.sender = senders.getFirst();
    }

    /**
     * 소속이 더 이상 유효하지 않은 신청을 발송 대상에서 빼고 <b>폐기한다.</b> 정리 훅(명세 06
     * §2 8단계)이 비켜간 잔여를 잡는 2차 방어다 — 근거와 실패 시나리오는 ADR space-team/0017.
     *
     * <p><b>건너뛰지 않고 지우는 이유</b>만 남긴다: 이 잔여를 나중에 치울 다른 주체가 없다
     * (멤버십 스윕은 열린 참여 행만 커서로 돌아 이 경우를 보지 못한다). 수신자를 못 찾는
     * 것과 다르다 — 그건 조회 실패라 보존하지만, 이건 소속 종료라는 확정된 사실이다.</p>
     *
     * @return 발송 대상으로 남은 신청. 판정 자체가 실패하면 원본을 그대로 돌려준다
     */
    private List<VacancyAlertRepository.WaitingAlert> discardStale(
            List<VacancyAlertRepository.WaitingAlert> candidates) {

        Set<Long> inactive;
        try {
            inactive = cohortMembershipQueryService.findInactiveMembershipIds(
                    candidates.stream()
                            .map(VacancyAlertRepository.WaitingAlert::cohortMembershipId)
                            .toList());
        } catch (Exception exception) {
            // 판정에 실패했다고 발송을 멈추지는 않는다. 유효한 대기자까지 함께 막히는 것이
            // 잔여 하나를 잘못 보내는 것보다 나쁘다 — 순서(CE-05)가 여전히 1차 방어다.
            log.error("공실 알림 수신자의 소속 판정에 실패해 그대로 진행합니다.", exception);
            return candidates;
        }

        if (inactive.isEmpty()) {
            return candidates;
        }

        try {
            int discarded = staleAlertDiscarder.discard(inactive);
            log.warn("소속이 끝난 신청자의 공실 알림 신청을 폐기했습니다. 폐기={}건 — "
                    + "정리 훅이 누락됐을 수 있습니다.", discarded);
        } catch (Exception exception) {
            // 폐기에 실패해도 발송 대상에서는 뺀다. 지우지 못한 행은 다음 공실에 다시 걸린다.
            log.error("소속이 끝난 공실 알림 신청 폐기에 실패했습니다.", exception);
        }

        return candidates.stream()
                .filter(candidate -> !inactive.contains(candidate.cohortMembershipId()))
                .toList();
    }

    /**
     * 이 회의실의 대기자 전원에게 발송한다.
     *
     * @param vacatedAt 비워진 시각. 스케줄러가 늦게 발견했어도 실제로 비워진 것은 이 시각이다
     * @return 실제로 발송·소진된 건수
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int dispatch(Long spaceId, OffsetDateTime vacatedAt) {

        List<VacancyAlertRepository.WaitingAlert> candidates =
                alertRepository.findWaitingBySpaceId(spaceId);
        if (candidates.isEmpty()) {
            return 0;
        }

        candidates = discardStale(candidates);
        if (candidates.isEmpty()) {
            return 0;
        }

        // 수신자를 여기서 한 번에 확정한다. 건별 Transaction 안에서 되물으면 대기자 수만큼
        // 기수 조회가 반복돼, findUserIds가 계약으로 정한 배치 성질이 깨진다.
        Map<Long, UUID> userIdByMembershipId = cohortMembershipQueryService.findUserIds(
                candidates.stream()
                        .map(VacancyAlertRepository.WaitingAlert::cohortMembershipId)
                        .toList());

        // 이름도 대기자 수와 무관하게 1회만 조회한다 — 이 dispatch 호출 안에서는 spaceId가
        // 하나로 고정되므로 배치가 아니라 단건 조회로 충분하다. 조회 실패(예: 공간이 그 사이
        // 삭제됨)가 발송 자체를 막으면 안 되므로 spaceId로 대체할 뿐 예외를 던지지 않는다.
        String spaceName = spaceNameQueryService.findName(spaceId).orElse("공간 " + spaceId);

        int sent = 0;
        for (VacancyAlertRepository.WaitingAlert candidate : candidates) {
            UUID recipientUserId = userIdByMembershipId.get(candidate.cohortMembershipId());

            // 소진시키지 않고 건너뛴다. 수신자를 못 찾는 것은 이 신청의 문제가 아니라 조회
            // 실패이고, 여기서 소진 처리하면 받지 못한 사람의 신청만 조용히 사라진다.
            if (recipientUserId == null) {
                log.warn("공실 알림 수신자를 찾지 못해 건너뜁니다. alertId={}, membershipId={}",
                        candidate.alertId(), candidate.cohortMembershipId());
                continue;
            }

            try {
                if (alertDelivery.send(candidate.alertId(), spaceId, spaceName,
                        recipientUserId, vacatedAt, sender)) {
                    sent++;
                }
            } catch (Exception exception) {
                log.error("공실 알림 발송에 실패했습니다. spaceId={}, alertId={}",
                        spaceId, candidate.alertId(), exception);
            }
        }

        // 후보 수와 발송 수를 함께 남긴다. 둘이 다르면 부분 실패이거나 그 사이 취소된
        // 건이 있다는 뜻이고, 로그에 하나만 남기면 어느 쪽인지 알 수 없다.
        log.info("공실 알림을 발송했습니다. spaceId={}, 대상={}건, 발송={}건",
                spaceId, candidates.size(), sent);
        return sent;
    }
}
