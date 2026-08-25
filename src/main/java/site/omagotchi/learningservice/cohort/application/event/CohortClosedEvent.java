package site.omagotchi.learningservice.cohort.application.event;

import java.time.OffsetDateTime;

/**
 * 교육 기수 하나가 종료됐다 (COH-F-04 → CE-05 훅).
 *
 * <p>기수 단위 이벤트로 둔 이유, {@link CohortMembershipEndedEvent}와 역할이 나뉘는 이유는
 * ADR space-team/0015. 발행 시점에는 이미 기수가 CLOSED이고 그 기수 소속이 전부 ENDED다 —
 * 같은 트랜잭션에서 함께 커밋되므로, <b>수신 측 대상 조회는 상태를 가리지 않아야 한다</b>
 * (활성으로 좁히면 이미 ENDED라 대상을 하나도 못 찾는다).</p>
 *
 * @param cohortId 종료된 기수. 팀 정리·공간 해제는 이 값으로 직접 조회하고, 대기 알림
 *                 삭제·점유 종료는 이 값으로 멤버십 목록을 얻어 처리한다 (명세 08 §1)
 * @param closedAt 종료 시각
 */
public record CohortClosedEvent(
        Long cohortId,
        OffsetDateTime closedAt
) {
}
