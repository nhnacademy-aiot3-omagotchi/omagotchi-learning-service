package site.omagotchi.learningservice.occupancy.application.event;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 공간 비활성화로 대기 중 공실 알림 신청이 삭제됐다 (RM-15).
 *
 * <p><b>수신자를 payload에 싣는 것이 이 이벤트의 특징이다.</b> {@code RoomVacatedEvent}는
 * "수신 대상은 리스너가 {@code vacancy_alerts}를 조회해 정한다"는 규약을 쓰지만, 여기서는
 * 그 행이 <b>이미 삭제된 뒤</b>라 조회할 대상이 남지 않는다. 그래서 지우기 전에 잡아
 * 함께 보낸다.</p>
 *
 * <p>계정이 아니라 멤버십을 싣는다. 신청이 멤버십 단위라 그것이 정본이고, 계정 변환은
 * 리스너가 배치로 한 번에 처리한다 — 여기서 변환하면 삭제 Transaction 안에서 기수 파트를
 * 부르게 된다.</p>
 *
 * <p>이 통보는 <b>at-most-once다</b> (명세 04 §4). 발송이 실패해도 재시도하지 않는다 —
 * 대상 행이 이미 없어 원천이 남지 않으며, 사용자는 신청 목록에서 사라진 것으로 상태를
 * 확인할 수 있다.</p>
 *
 * @param spaceId             비활성화된 공간
 * @param cohortMembershipIds 삭제된 신청의 주체들. 비어 있으면 발행하지 않는다
 * @param discardedAt         삭제 시각
 */
public record VacancyAlertsDiscardedEvent(
        Long spaceId,
        List<Long> cohortMembershipIds,
        OffsetDateTime discardedAt
) {

    public VacancyAlertsDiscardedEvent {
        cohortMembershipIds = List.copyOf(cohortMembershipIds);
    }
}
