package site.omagotchi.learningservice.occupancy.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.occupancy.application.event.RoomVacatedEvent;
import site.omagotchi.learningservice.occupancy.application.port.OccupancyEventPublisher;

/**
 * {@link OccupancyEventPublisher}를 Spring 이벤트로 구현한다.
 *
 * <p>얇은 위임인데도 클래스를 두는 이유는 {@code ApplicationEventPublisher}가 Framework
 * 타입이기 때문이다. Application이 이것을 직접 주입받으면 발행 수단을 바꿀 때
 * (예: 아웃박스나 큐) 서비스 코드가 함께 바뀐다.</p>
 *
 * <p>수신은 {@code RoomVacatedListener}가 한다 (명세서 04). 리스너가 지켜야 할 계약은
 * Port의 javadoc에 있다 — {@code AFTER_COMMIT} + {@code @Async} + 건별 {@code REQUIRES_NEW}
 * (ADR space-team/0006).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpringOccupancyEventPublisher implements OccupancyEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publishRoomVacated(RoomVacatedEvent event) {
        log.debug("공실 이벤트 발행. spaceId={}, occupancyId={}", event.spaceId(), event.occupancyId());
        applicationEventPublisher.publishEvent(event);
    }
}
