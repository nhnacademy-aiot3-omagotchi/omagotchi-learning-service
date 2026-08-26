package site.omagotchi.learningservice.team.application.port;

import site.omagotchi.learningservice.team.application.event.TeamDisbandedEvent;

/**
 * 팀 도메인 이벤트의 발행 경계.
 *
 * <p>Port를 두는 이유는 Application이 Spring의 {@code ApplicationEventPublisher}를 알면
 * 안 되기 때문이다 (10-backend-code-structure §5, Message 경계). 나중에 큐로 옮기더라도
 * 이 인터페이스 뒤의 구현만 바뀐다 — 점유의 {@code OccupancyEventPublisher}와 같은 구조다.</p>
 */
public interface TeamEventPublisher {

    /**
     * 팀이 해체됐음을 알린다 (GR-19).
     *
     * <p>호출 시점은 해체와 같은 Transaction 안이지만 실제 수신은 커밋 후다 — 리스너의
     * {@code AFTER_COMMIT}이 그 시점을 정한다. 여기서 커밋을 기다리지 않는다.</p>
     */
    void publishTeamDisbanded(TeamDisbandedEvent event);
}
