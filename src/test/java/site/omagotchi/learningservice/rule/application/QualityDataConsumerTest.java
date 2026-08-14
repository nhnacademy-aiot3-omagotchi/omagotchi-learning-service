package site.omagotchi.learningservice.rule.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import site.omagotchi.learningservice.environment.presentation.message.QualityEvent;
import site.omagotchi.learningservice.environment.presentation.message.QualityMessageConsumer;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("품질 이벤트 컨슈머")
class QualityDataConsumerTest {

    private static final String TRACE_ID = "trace-0001";
    private static final String DEVICE_EUI = "0011223344556677";
    private static final String MEASUREMENT = "co2";
    private static final Instant MEASURED_AT = Instant.parse("2026-08-10T00:00:00Z");
    private static final Instant RECEIVED_AT = Instant.parse("2026-08-10T00:00:01Z");

    private final QualityMessageConsumer consumer = new QualityMessageConsumer();

    @ParameterizedTest
    @EnumSource(QualityEvent.Type.class)
    @DisplayName("모든 이벤트 유형을 예외 없이 소비한다")
    void consumes(QualityEvent.Type type) {
        QualityEvent event = new QualityEvent(
                1, TRACE_ID, type, "3층", "창가", DEVICE_EUI, MEASUREMENT,
                4_200.0, MEASURED_AT, RECEIVED_AT, "co2 4200 > 임계 1000");

        assertDoesNotThrow(() -> consumer.consume(event, TRACE_ID));
    }

    @Test
    @DisplayName("추적 식별자와 측정값이 없어도 소비한다")
    void consumesWithoutOptionals() {
        // MISSING 은 프레임 자체가 없어서 value 가 null 로 온다
        QualityEvent event = new QualityEvent(
                1, null, QualityEvent.Type.MISSING, "3층", "창가", DEVICE_EUI, MEASUREMENT,
                null, MEASURED_AT, RECEIVED_AT, "fCnt 갭");

        assertDoesNotThrow(() -> consumer.consume(event, null));
    }

    @Test
    @DisplayName("유형이 없는 이벤트의 처리 방식을 고정한다")
    void handlesNullType() {
        QualityEvent event = new QualityEvent(
                1, TRACE_ID, null, "3층", "창가", DEVICE_EUI, MEASUREMENT,
                4_200.0, MEASURED_AT, RECEIVED_AT, "판정 없음");

        // 현재 구현은 type 을 그대로 역참조하므로 NPE 가 나고, 재시도 후 DLQ 로 이관된다.
        // 컨슈머에서 방어하기로 정하면 이 테스트가 바뀌어야 한다.
        assertThrows(NullPointerException.class, () -> consumer.consume(event, TRACE_ID));
    }
}
