package site.omagotchi.learningservice.cohort.application.event;

import java.time.OffsetDateTime;

/**
 * 교육 기수 하나가 종료됐다 (COH-F-04 → CE-05 훅).
 *
 * <p><b>기수 단위 이벤트인 것이 핵심이다.</b> 같은 사실을 멤버십 단위로 팬아웃하면 비동기라
 * CE-05가 강제하는 순서(팀 정리 → 대기 알림 삭제 → 점유 종료 → 실습실 해제)를 보장할 수
 * 없다. 특히 알림 삭제가 점유 종료보다 뒤로 밀리면 <b>방금 종료된 기수 학생에게 공실
 * 알림이 발송된다.</b> 게다가 실습실 배정 해제(CE-04)는 {@code spaces.cohort_id} 기준이라
 * 멤버십 이벤트로는 표현되지도 않는다.</p>
 *
 * <p>그래서 {@link CohortMembershipEndedEvent}와 역할이 나뉜다. 저쪽은 계정 삭제·수동
 * 제명처럼 <b>소속 하나</b>가 끝나는 사건이고 순서 제약이 없다. 기수 종료가 멤버십을 일괄
 * ENDED로 전이하면서도 그 이벤트를 내지 않는 이유가 여기 있다
 * ({@code CohortMembershipRepository#endActiveByCohortId}).</p>
 *
 * <p>발행 시점에는 이미 기수가 CLOSED이고 그 기수 소속이 전부 ENDED다 — 같은 트랜잭션에서
 * 함께 커밋된다. 수신 측은 <b>정지된 대상</b>을 보게 되므로, 정리 도중에 새 점유·신청이
 * 끼어들 수 없다. 다만 멤버십이 이미 ENDED라 대상 조회는 상태를 가리지 않아야 한다.</p>
 *
 * <p>수신 측 규약은 {@code OccupancyEventPublisher}의 javadoc과 같다 (ADR space-team/0006)
 * — {@code AFTER_COMMIT} + {@code @Async}. 단 단계별 Transaction은 훅 내부가 직접 나누므로
 * 리스너에 {@code REQUIRES_NEW}를 걸지 않는다.</p>
 *
 * @param cohortId 종료된 기수. 팀 정리·실습실 해제는 이 값으로 직접 조회하고, 대기 알림
 *                 삭제·점유 종료는 이 값으로 멤버십 목록을 얻어 처리한다 (명세 08 §1)
 * @param closedAt 종료 시각
 */
public record CohortClosedEvent(
        Long cohortId,
        OffsetDateTime closedAt
) {
}
