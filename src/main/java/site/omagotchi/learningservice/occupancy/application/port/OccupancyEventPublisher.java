package site.omagotchi.learningservice.occupancy.application.port;

import site.omagotchi.learningservice.occupancy.application.event.RoomVacatedEvent;

/**
 * 점유 도메인 이벤트의 발행 경계.
 *
 * <p>Port를 두는 이유는 Application이 Spring의 {@code ApplicationEventPublisher}를 알면
 * 안 되기 때문이다 (10-backend-code-structure §5, Message 경계). 나중에 큐로 옮기더라도
 * 이 인터페이스 뒤의 구현만 바뀐다.</p>
 *
 * <p><b>리스너를 만들 때 지켜야 할 것</b> (ADR space-team/0006):</p>
 * <ul>
 *   <li>{@code @TransactionalEventListener(phase = AFTER_COMMIT)} — 커밋 전에 받으면
 *       롤백된 데이터를 참조해 알림을 보낸다</li>
 *   <li>{@code @Async} — 발송 실패가 점유 해제를 롤백시키면 안 된다 (MR-18).
 *       스레드 풀과 예외 로깅을 명시적으로 설정할 것(기본 설정 방치 금지)</li>
 *   <li>{@code @Transactional(propagation = REQUIRES_NEW)} — 원 트랜잭션이 이미 끝난
 *       상태라, <b>빠뜨리면 {@code notified_at} 기록이 조용히 유실된다.</b>
 *       ADR이 리뷰 체크 항목으로 지정한 함정이다</li>
 * </ul>
 *
 * <p>발송 실패는 재시도하지 않는다. 성공 건만 {@code notified_at}으로 소진 처리하고
 * 실패 건은 NULL로 남겨 다음 공실 때 자연 재시도되게 한다 — 알림은 사용 권한을 보장하지
 * 않으므로(MR-04) 큐 수준의 전달 보장이 필요 없다는 것이 ADR의 결론이다.</p>
 */
public interface OccupancyEventPublisher {

    /**
     * 회의실이 비었음을 알린다.
     *
     * <p>호출 시점은 상태 변경과 같은 트랜잭션 안이지만, 실제 수신은 커밋 후다 —
     * 리스너의 {@code AFTER_COMMIT}이 그 시점을 정한다. 여기서 커밋을 기다리지 않는다.</p>
     */
    void publishRoomVacated(RoomVacatedEvent event);
}
