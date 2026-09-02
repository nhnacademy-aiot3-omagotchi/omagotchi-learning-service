package site.omagotchi.learningservice.environment.presentation.web.response;

import org.junit.jupiter.api.Test;
import site.omagotchi.learningservice.environment.application.query.SensorEventItem;
import site.omagotchi.learningservice.environment.domain.SensorDetection;
import site.omagotchi.learningservice.environment.domain.SensorEvent;
import site.omagotchi.learningservice.environment.domain.SensorEventType;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SensorEventResponseTest {

    @Test
    void exposesEventIdSeparatelyFromSharedTraceId() {
        Instant receivedAt = Instant.parse("2026-09-02T10:00:00Z");
        SensorEvent event = SensorEvent.of(new SensorDetection(
                "shared-trace-id",
                SensorEventType.ANOMALY,
                "실습실",
                null,
                "device-1",
                "co2",
                1200.0,
                "범위 초과",
                null,
                null,
                receivedAt.minusSeconds(1),
                receivedAt
        ));

        SensorEventResponse response = SensorEventResponse.from(
                new SensorEventItem(event, "실습실 센서"));

        assertThat(response.eventId()).isEqualTo(event.id());
        assertThat(response.traceId()).isEqualTo("shared-trace-id");
    }
}
