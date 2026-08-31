package site.omagotchi.learningservice.team.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.team.application.port.TeamNotificationSender;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 해체된 팀의 (구)팀원에게 통보한다 (GR-19, 명세 06 §2 4단계).
 *
 * <p><b>통보가 필요한 유일한 해체 경로다.</b> 소속 종료로 인한 자동 해체(GR-16)는 마지막
 * 한 명마저 떠난 상황이라 받을 사람이 없고, 기수 종료(CE-01)는 서비스 이용 자체가 끝나
 * 안내 실익이 없다. 마스터의 [팀 삭제]만 <b>팀원이 인지하지 못한 채 팀이 사라지는</b>
 * 경우라 알려야 한다.</p>
 *
 * <p><b>전달 보장은 at-most-once다</b> (명세 06 §5). 발송이 실패해도 재시도하지 않는다 —
 * 통보 대상인 {@code team_members} 행을 이미 지운 뒤라 재시도할 원천이 남지 않기 때문이다.
 * 그래서 여기서는 발송 기록도, 건별 Transaction도 필요 없다 — 남길 상태가 없다.</p>
 *
 * <p><b>조회는 배치다.</b> (구)팀원이 N명이어도 계정 조회 1회로 고정된다 —
 * {@code VacancyAlertDiscardNotifier}와 같은 규약이며, 건별로 되물으면
 * {@code findUserIds}가 계약으로 정한 배치 성질이 깨진다.</p>
 */
@Slf4j
@Service
public class TeamDisbandNotifier {

    private final CohortMembershipQueryService cohortMembershipQueryService;

    private final TeamNotificationSender sender;

    /**
     * <b>{@code List}로 받아야 한다.</b> {@code Optional}이나 단건으로 받으면 후보가 둘이어도
     * {@code @Primary} 하나가 모호성을 없애 버려 <b>나머지가 조용히 무시된다.</b>
     * {@code List}만 후보 전부를 보여 준다.
     */
    public TeamDisbandNotifier(
            CohortMembershipQueryService cohortMembershipQueryService,
            List<TeamNotificationSender> senders
    ) {
        this.cohortMembershipQueryService = cohortMembershipQueryService;

        // 어느 발송의 성공을 완료로 볼지 정할 수 없는 설정이므로 기동 시점에 멈춘다.
        // 0개도 오류다 — 발송 수단 없이 이 서비스가 할 일은 없다.
        if (senders.size() != 1) {
            throw new IllegalStateException("팀 알림 sender는 정확히 하나여야 합니다: " + senders);
        }
        this.sender = senders.getFirst();
    }

    /**
     * (구)팀원 전원에게 해체 통보를 보낸다.
     *
     * <p>한 사람의 실패가 나머지를 막지 않는다. 여기서 전파시키면 뒤쪽 사람들이
     * <b>앞사람의 발송 실패 때문에</b> 통보를 받지 못한다.</p>
     *
     * @param cohortMembershipIds (구)팀원들. 이벤트가 실어 온 값이다 — 행이 이미 없어
     *                            여기서 다시 조회할 수 없다
     * @return 실제로 발송한 건수
     */
    public int notifyDisbanded(
            Long teamId, String teamName, List<Long> cohortMembershipIds, OffsetDateTime disbandedAt) {

        if (cohortMembershipIds.isEmpty()) {
            return 0;
        }

        Map<Long, UUID> userIdByMembershipId =
                cohortMembershipQueryService.findUserIds(cohortMembershipIds);

        int sent = 0;
        for (Long membershipId : cohortMembershipIds) {
            UUID recipientUserId = userIdByMembershipId.get(membershipId);
            if (recipientUserId == null) {
                log.warn("팀 해체 통보 수신자를 찾지 못해 건너뜁니다. teamId={}, membershipId={}",
                        teamId, membershipId);
                continue;
            }

            try {
                sender.sendDisbandNotice(new TeamNotificationSender.DisbandNotice(
                        teamId, teamName, recipientUserId, disbandedAt));
                sent++;
            } catch (Exception exception) {
                // 재시도하지 않는다. 남길 원천이 없으므로 기록이 유일한 대응이다.
                log.error("팀 해체 통보에 실패했습니다. teamId={}, membershipId={}",
                        teamId, membershipId, exception);
            }
        }

        log.info("팀 해체 통보를 발송했습니다. teamId={}, 대상={}건, 발송={}건",
                teamId, cohortMembershipIds.size(), sent);
        return sent;
    }
}
