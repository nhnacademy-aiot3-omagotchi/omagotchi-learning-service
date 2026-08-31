package site.omagotchi.learningservice.environment.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.environment.application.port.SensorEventStore;
import site.omagotchi.learningservice.environment.domain.SensorDetection;
import site.omagotchi.learningservice.environment.domain.SensorEvent;
import site.omagotchi.learningservice.environment.domain.SensorEventType;
import site.omagotchi.learningservice.sensor.application.SensorDeviceService;
import site.omagotchi.learningservice.sensor.application.result.SensorDeviceResult;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SensorEventQueryServiceTest {

    private static final Long COHORT_ID = 1L;
    private static final UUID REQUESTER_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000001"
    );
    private static final Instant NOW = Instant.parse("2026-08-30T10:00:00Z");
    private static final String ALLOWED_EUI = "0011223344556677";
    private static final String OTHER_EUI = "8899aabbccddeeff";

    @Mock
    private SensorEventStore sensorEventStore;

    @Mock
    private SensorDeviceService sensorDeviceService;

    private SensorEventQueryService sensorEventQueryService;

    @BeforeEach
    void setUp() {
        sensorEventQueryService = new SensorEventQueryService(
                sensorEventStore,
                sensorDeviceService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void filtersOtherCohortEventsBeforePagination() {
        when(sensorDeviceService.findAll(COHORT_ID, REQUESTER_ID))
                .thenReturn(List.of(new SensorDeviceResult(
                        ALLOWED_EUI,
                        10L,
                        "실습실 센서",
                        "AM103",
                        null,
                        60,
                        true
                )));
        when(sensorEventStore.findByReceivedAt(
                NOW.minusSeconds(24 * 60 * 60),
                NOW
        )).thenReturn(List.of(event(ALLOWED_EUI), event(OTHER_EUI)));

        var result = sensorEventQueryService.getEvents(
                COHORT_ID,
                REQUESTER_ID,
                null,
                null,
                null,
                null,
                0,
                20
        );

        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().event().detection().deviceEui())
                .isEqualTo(ALLOWED_EUI);
        assertThat(result.items().getFirst().deviceDisplayName())
                .isEqualTo("실습실 센서");
        verify(sensorDeviceService).findAll(COHORT_ID, REQUESTER_ID);
    }

    private SensorEvent event(String deviceEui) {
        return SensorEvent.of(new SensorDetection(
                "trace-1",
                SensorEventType.ANOMALY,
                "실습실",
                null,
                deviceEui,
                "co2",
                1000.0,
                null,
                null,
                null,
                NOW.minusSeconds(10),
                NOW.minusSeconds(5)
        ));
    }
}
