package site.omagotchi.learningservice.occupancy.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import site.omagotchi.learningservice.occupancy.application.event.RoomVacatedEvent;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.mockito.Mockito.verify;

/**
 * 이벤트 발행 어댑터.
 *
 * <p>얇은 위임이지만 테스트를 두는 이유는 이 클래스가 Application과 Spring 사이의 유일한
 * 접점이기 때문이다. 여기서 이벤트를 변형하거나 삼키면 공실 알림이 조용히 사라진다.</p>
 */
@ExtendWith(MockitoExtension.class)
class SpringOccupancyEventPublisherTest {

    private static final OffsetDateTime VACATED_AT =
            OffsetDateTime.of(2026, 7, 24, 12, 0, 0, 0, ZoneOffset.ofHours(9));

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private SpringOccupancyEventPublisher springOccupancyEventPublisher;

    @Test
    @DisplayName("공실 이벤트를 변형 없이 그대로 발행한다.")
    void test1() {
        RoomVacatedEvent event = new RoomVacatedEvent(1L, 100L, VACATED_AT);

        springOccupancyEventPublisher.publishRoomVacated(event);

        verify(applicationEventPublisher).publishEvent(event);
    }
}
