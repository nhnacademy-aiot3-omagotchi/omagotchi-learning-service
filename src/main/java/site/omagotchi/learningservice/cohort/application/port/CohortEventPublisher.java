package site.omagotchi.learningservice.cohort.application.port;

import site.omagotchi.learningservice.cohort.application.event.CohortClosedEvent;
import site.omagotchi.learningservice.cohort.application.event.CohortMembershipEndedEvent;

/**
 * 기수 도메인 이벤트의 발행 경계.
 *
 * <p>Port를 두는 이유는 Application이 Spring의 {@code ApplicationEventPublisher}를 알면
 * 안 되기 때문이다 (10-backend-code-structure §5, Message 경계). 나중에 큐로 옮기더라도
 * 이 인터페이스 뒤의 구현만 바뀐다 — 점유의 {@code OccupancyEventPublisher}와 같은 구조다.</p>
 */
public interface CohortEventPublisher {

    /**
     * 기수 소속 하나가 끝났음을 알린다.
     *
     * <p>호출 시점은 상태 변경과 같은 트랜잭션 안이지만 실제 수신은 커밋 후다 —
     * 리스너의 {@code AFTER_COMMIT}이 그 시점을 정한다. 여기서 커밋을 기다리지 않는다.</p>
     */
    void publishMembershipEnded(CohortMembershipEndedEvent event);

    /**
     * 교육 기수 하나가 종료됐음을 알린다 (CE-05 훅의 진입점).
     *
     * <p>{@link #publishMembershipEnded}와 나란히 두지만 성격이 다르다 — 이쪽은 기수 단위
     * 단발이고, 수신 측이 정해진 순서로 4단계를 밟는다 (ADR space-team/0015).</p>
     */
    void publishCohortClosed(CohortClosedEvent event);
}
