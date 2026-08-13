package site.omagotchi.learningservice.rule.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory.ConfirmType;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.boot.amqp.autoconfigure.RabbitProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;


@DisplayName("파킹 큐 recoverer 설정")
class RabbitRecoverConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(StubBeans.class, RabbitRecoverConfig.class);

    @Test
    @DisplayName("correlated 면 recoverer 를 만든다")
    void createsRecovererWhenCorrelated() {
        contextRunner
                .withBean(RabbitProperties.class, () -> properties(ConfirmType.CORRELATED))
                .run(context -> assertThat(context)
                        .hasNotFailed()
                        .hasSingleBean(MessageRecoverer.class));
    }

    @ParameterizedTest
    @EnumSource(value = ConfirmType.class, names = "CORRELATED", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("correlated 가 아니면 기동을 막는다")
    void failsFastWhenNotCorrelated(ConfirmType confirmType) {
        contextRunner
                .withBean(RabbitProperties.class, () -> properties(confirmType))
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("correlated"));
    }

    @Test
    @DisplayName("설정이 없으면(null) 기동을 막는다")
    void failsFastWhenUnset() {
        // 프로퍼티 미설정 시 getPublisherConfirmType() 은 null 을 돌려준다
        contextRunner
                .withBean(RabbitProperties.class, RabbitProperties::new)
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(IllegalStateException.class));
    }

    private RabbitProperties properties(ConfirmType confirmType) {
        RabbitProperties rabbitProperties = new RabbitProperties();
        rabbitProperties.setPublisherConfirmType(confirmType);
        return rabbitProperties;
    }

    /** recoverer 조립에만 필요한 협력자. 브로커 연결은 일어나지 않는다. */
    @Configuration(proxyBeanMethods = false)
    static class StubBeans {

        @Bean
        RabbitTemplate rabbitTemplate() {
            return Mockito.mock(RabbitTemplate.class);
        }

        @Bean
        RecoveryMetrics recoveryMetrics() {
            return Mockito.mock(RecoveryMetrics.class);
        }
    }
}
