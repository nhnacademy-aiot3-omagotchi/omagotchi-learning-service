package site.omagotchi.learningservice.team.application.event;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 마스터가 팀을 해체했다 (GR-19).
 *
 * <p><b>수신자를 payload에 싣는 것이 이 이벤트의 특징이다.</b> 해체는 {@code team_members}
 * 전 행을 <b>물리 삭제</b>하므로, 커밋 후 리스너가 도는 시점에는 "누가 팀원이었는지"를
 * 조회할 방법이 없다. 그래서 지우기 전에 잡아 함께 보낸다 —
 * {@code VacancyAlertsDiscardedEvent}(RM-15)와 같은 이유이며, 대상을 리스너가 조회하는
 * {@code RoomVacatedEvent}와는 반대다.</p>
 *
 * <p>계정이 아니라 멤버십을 싣는다. 팀원이 멤버십 단위라 그것이 정본이고, 계정 변환은
 * 리스너가 배치로 한 번에 처리한다 — 여기서 변환하면 해체 Transaction 안에서 기수 파트를
 * 부르게 된다.</p>
 *
 * <p><b>해체한 마스터 본인은 목록에서 빠진다.</b> 자기가 방금 누른 버튼의 결과를 통보로
 * 다시 알릴 이유가 없다 — 발행자가 걸러서 넣는다.</p>
 *
 * <p>이 통보는 <b>at-most-once다</b> (명세 06 §5). 발송이 실패해도 재시도하지 않는다 —
 * 대상 행이 이미 없어 원천이 남지 않으며, 사용자는 팀 목록에서 사라진 것으로 상태를
 * 확인할 수 있다.</p>
 *
 * @param teamId              해체된 팀
 * @param teamName            해체 시점의 팀 이름. 사람이 읽는 문구에 쓴다
 * @param cohortMembershipIds (구)팀원들. 해체한 마스터는 제외돼 있고, 비어 있으면 발행하지 않는다
 * @param disbandedAt         해체 시각
 */
public record TeamDisbandedEvent(
        Long teamId,
        String teamName,
        List<Long> cohortMembershipIds,
        OffsetDateTime disbandedAt
) {

    public TeamDisbandedEvent {
        cohortMembershipIds = List.copyOf(cohortMembershipIds);
    }
}
