package site.omagotchi.learningservice.rule.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import site.omagotchi.learningservice.global.config.AmqpMessageConverterConfig;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * rule-service 가 보내는 메세지가 이 서비스의 이벤트로 복원되는지 검증한다.
 * <p/>
 * 컨버터를 직접 new 하지 않고 컨텍스트에서 꺼내는 것이 요점이다. 기본 생성자는 자기 매퍼를
 * 새로 만들어 spring.jackson.* 설정과 JsonMapperBuilderCustomizer 가 빠지므로,
 * 프로덕션과 다른 직렬화기를 검증하게 된다.
 */
@DisplayName("품질 이벤트 메세지 변환")
class QualityEventMessageConverterTest {

    private static final String TRACE_ID = "trace-0001";
    private static final String DEVICE_EUI = "0011223344556677";
    private static final String MEASUREMENT = "co2";
    private static final Instant MEASURED_AT = Instant.parse("2026-08-10T00:00:00Z");
    private static final Instant RECEIVED_AT = Instant.parse("2026-08-10T00:00:01Z");

    // 전체 컨텍스트를 띄우지 않고 Jackson 자동 구성과 토폴로지 설정만 올린다.
    // 큐·익스체인지 빈은 객체일 뿐이라 브로커 연결이 필요 없다.
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
            .withUserConfiguration(AmqpMessageConverterConfig.class);

    @Test
    @DisplayName("rule-service 페이로드를 이벤트로 복원한다")
    void deserializes() {
        contextRunner.run(context -> {
            JacksonJsonMessageConverter converter = context.getBean(JacksonJsonMessageConverter.class);

            QualityEvent event = (QualityEvent) converter.fromMessage(rulePayloadMessage());

            assertThat(event.version()).isEqualTo(1);
            assertThat(event.traceId()).isEqualTo(TRACE_ID);
            assertThat(event.type()).isEqualTo(QualityEvent.Type.RULE_HIT);
            assertThat(event.deviceEui()).isEqualTo(DEVICE_EUI);
            assertThat(event.measurement()).isEqualTo(MEASUREMENT);
            assertThat(event.value()).isEqualTo(4_200.0);
            assertThat(event.measuredAt()).isEqualTo(MEASURED_AT);
            assertThat(event.receivedAt()).isEqualTo(RECEIVED_AT);
        });
    }

    /**
     * 발행 측이 붙이는 __TypeId__ 는 이 서비스에 없는 클래스를 가리킨다.
     * 그래도 복원되는 이유는 타입 해석이 INFERRED(리스너 파라미터 타입) 우선이기 때문이다.
     * 이 전제가 깨지면 모든 메세지가 역직렬화에 실패해 DLQ로 간다.
     */
    @Test
    @DisplayName("발행 측 __TypeId__ 가 없는 클래스를 가리켜도 파라미터 타입으로 복원한다")
    void deserializesDespiteForeignTypeId() {
        contextRunner.run(context -> {
            JacksonJsonMessageConverter converter = context.getBean(JacksonJsonMessageConverter.class);

            Message message = rulePayloadMessage();
            message.getMessageProperties()
                    .setHeader("__TypeId__", "site.omagotchi.ruleservice.quality.domain.QualityEvent");

            assertThat(converter.fromMessage(message)).isInstanceOf(QualityEvent.class);
        });
    }

    /**
     * 발행 측이 필드를 늘려도 소비가 계속돼야 한다.
     * 모르는 필드에서 실패하면 스키마를 조금만 확장해도 전부 DLQ로 간다.
     */
    @Test
    @DisplayName("모르는 필드가 있어도 무시하고 복원한다")
    void ignoresUnknownField() {
        contextRunner.run(context -> {
            JacksonJsonMessageConverter converter = context.getBean(JacksonJsonMessageConverter.class);

            Message message = message("""
                    {
                      "version": 2,
                      "traceId": "trace-0001",
                      "type": "ANOMALY",
                      "deviceEui": "0011223344556677",
                      "measurement": "co2",
                      "measuredAt": "2026-08-10T00:00:00Z",
                      "receivedAt": "2026-08-10T00:00:01Z",
                      "detail": "범위 초과",
                      "severity": "HIGH"
                    }
                    """);

            QualityEvent event = (QualityEvent) converter.fromMessage(message);

            assertThat(event.type()).isEqualTo(QualityEvent.Type.ANOMALY);
        });
    }

    /** rule-service 가 실제로 보내는 형태의 메세지 */
    private Message rulePayloadMessage() {
        return message("""
                {
                  "version": 1,
                  "traceId": "trace-0001",
                  "type": "RULE_HIT",
                  "location": "3층",
                  "point": "창가",
                  "deviceEui": "0011223344556677",
                  "measurement": "co2",
                  "value": 4200.0,
                  "measuredAt": "2026-08-10T00:00:00Z",
                  "receivedAt": "2026-08-10T00:00:01Z",
                  "detail": "co2 4200 > 임계 1000"
                }
                """);
    }

    private Message message(String payload) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        // 리스너 어댑터가 consume(QualityEvent, ...) 시그니처를 보고 채워 넣는 값
        properties.setInferredArgumentType(QualityEvent.class);

        return new Message(payload.getBytes(StandardCharsets.UTF_8), properties);
    }
}
