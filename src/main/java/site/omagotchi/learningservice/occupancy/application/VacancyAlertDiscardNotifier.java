package site.omagotchi.learningservice.occupancy.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.occupancy.application.port.VacancyAlertSender;
import site.omagotchi.learningservice.space.application.SpaceNameQueryService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 공간 비활성화로 삭제된 신청의 (구)신청자에게 통보한다 (RM-15).
 *
 * <p><b>통보가 필요한 유일한 삭제 경로다</b> (명세 04 §3). 본인 취소는 자기가 한 행동이고,
 * 강제 종료·기수 종료는 안내 실익이 없다. 비활성화만 <b>사용자가 인지하지 못한 채 알림이
 * 사라지는</b> 경우라 알려야 한다.</p>
 *
 * <p><b>전달 보장은 at-most-once다</b> (명세 04 §4). 발송이 실패해도 재시도하지 않는다 —
 * 통보 대상 행을 이미 지운 뒤라 재시도할 원천이 남지 않기 때문이다. 공실 알림
 * 발송({@link VacancyAlertDispatcher})이 at-least-once인 것과 대비된다. 그래서 여기서는
 * 소진 기록도, 건별 Transaction도 필요 없다 — 남길 상태가 없다.</p>
 *
 * <p>대가는 "알림이 사라진 것을 통보로 알지 못한다"에 그친다. 사용자는 신청 목록에서
 * 사라진 것으로 확인할 수 있고, 공간이 비활성이라는 사실도 목록에 드러난다.</p>
 *
 * <p><b>조회는 배치다.</b> (구)신청자가 N명이어도 계정 조회 1회 + 이름 조회 1회로 고정된다
 * — {@code VacancyAlertDispatcher}와 같은 규약이며, 건별로 되물으면
 * {@code findUserIds}가 계약으로 정한 배치 성질이 깨진다.</p>
 */
@Slf4j
@Service
public class VacancyAlertDiscardNotifier {

    private final CohortMembershipQueryService cohortMembershipQueryService;
    private final SpaceNameQueryService spaceNameQueryService;
    private final VacancyAlertSender sender;

    /**
     * <b>{@code List}로 받아야 한다.</b> {@code Optional}이나 단건으로 받으면 후보가 둘이어도
     * {@code @Primary} 하나가 모호성을 없애 버려 <b>나머지가 조용히 무시된다.</b>
     * {@code List}만 후보 전부를 보여 준다.
     */
    public VacancyAlertDiscardNotifier(
            CohortMembershipQueryService cohortMembershipQueryService,
            SpaceNameQueryService spaceNameQueryService,
            List<VacancyAlertSender> senders
    ) {
        this.cohortMembershipQueryService = cohortMembershipQueryService;
        this.spaceNameQueryService = spaceNameQueryService;

        // 어느 발송의 성공을 완료로 볼지 정할 수 없는 설정이므로 기동 시점에 멈춘다.
        // 0개도 오류다 — 발송 수단 없이 이 서비스가 할 일은 없다.
        if (senders.size() != 1) {
            throw new IllegalStateException("공실 알림 sender는 정확히 하나여야 합니다: " + senders);
        }
        this.sender = senders.getFirst();
    }

    /**
     * (구)신청자 전원에게 삭제 통보를 보낸다.
     *
     * <p>한 사람의 실패가 나머지를 막지 않는다. 여기서 전파시키면 뒤쪽 사람들이
     * <b>앞사람의 발송 실패 때문에</b> 통보를 받지 못한다.</p>
     *
     * @param cohortMembershipIds 삭제된 신청의 주체들. 이벤트가 실어 온 값이다 — 행이 이미
     *                            없어 여기서 다시 조회할 수 없다
     * @return 실제로 발송한 건수
     */
    public int notifyDiscarded(
            Long spaceId, List<Long> cohortMembershipIds, OffsetDateTime discardedAt) {

        if (cohortMembershipIds.isEmpty()) {
            return 0;
        }

        Map<Long, UUID> userIdByMembershipId =
                cohortMembershipQueryService.findUserIds(cohortMembershipIds);

        // 이름 조회 실패가 통보 자체를 막으면 안 된다 — 사용자는 어느 방인지 몰라도
        // 신청이 사라졌다는 사실은 알아야 한다.
        String spaceName = spaceNameQueryService.findName(spaceId).orElse("공간 " + spaceId);

        int sent = 0;
        for (Long membershipId : cohortMembershipIds) {
            UUID recipientUserId = userIdByMembershipId.get(membershipId);
            if (recipientUserId == null) {
                log.warn("삭제 통보 수신자를 찾지 못해 건너뜁니다. spaceId={}, membershipId={}",
                        spaceId, membershipId);
                continue;
            }

            try {
                sender.sendDiscardNotice(new VacancyAlertSender.DiscardNotice(
                        spaceId, spaceName, recipientUserId, discardedAt));
                sent++;
            } catch (Exception exception) {
                // 재시도하지 않는다. 남길 원천이 없으므로 기록이 유일한 대응이다.
                log.error("공실 알림 삭제 통보에 실패했습니다. spaceId={}, membershipId={}",
                        spaceId, membershipId, exception);
            }
        }

        log.info("공실 알림 삭제 통보를 발송했습니다. spaceId={}, 대상={}건, 발송={}건",
                spaceId, cohortMembershipIds.size(), sent);
        return sent;
    }
}
