package site.omagotchi.learningservice.study.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import site.omagotchi.learningservice.study.domain.TimerTimePolicy;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("타이머 시간 설정")
class TimerClockConfigTest {

    private static final Instant STARTED_AT = Instant.parse("2000-01-01T00:00:00Z");

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(context -> context.getBeanFactory().setConversionService(
                    ApplicationConversionService.getSharedInstance()
            ))
            .withUserConfiguration(TimerTimeConfig.class);

    @Test
    @DisplayName("설정값으로 시간 정책 Bean 생성")
    void createsTimePolicyFromConfiguredDuration() {
        contextRunner.withPropertyValues("timer.max-duration=PT37M").run(context -> {
            TimerTimePolicy policy = context.getBean(TimerTimePolicy.class);

            assertEquals(
                    Instant.parse("2000-01-01T00:37:00Z"),
                    policy.expirationAt(STARTED_AT)
            );
        });
    }

    @Test
    @DisplayName("필수 Duration 누락 시 Context 시작 실패")
    void rejectsMissingDuration() {
        contextRunner.run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT0S", "-PT1S", "not-a-duration"})
    @DisplayName("유효하지 않은 Duration이면 Context 시작 실패")
    void rejectsInvalidDuration(String duration) {
        contextRunner
                .withPropertyValues("timer.max-duration=" + duration)
                .run(context -> assertThat(context).hasFailed());
    }
}
