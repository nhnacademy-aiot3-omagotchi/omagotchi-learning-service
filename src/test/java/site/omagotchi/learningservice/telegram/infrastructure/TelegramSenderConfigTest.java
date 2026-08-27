package site.omagotchi.learningservice.telegram.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import site.omagotchi.learningservice.telegram.application.TelegramNotificationService;
import site.omagotchi.learningservice.telegram.application.TelegramProperties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 발송 Bean의 등록 조건을 고정한다.
 *
 * <p><b>이 테스트가 필요한 이유는 실패가 조용하기 때문이다.</b> {@code @ConditionalOnProperty}는
 * 키가 없으면 예외 없이 비활성화된다 — 실제로 조건 경로가 설정과 한 단계 어긋나 있어
 * ({@code telegram.notification.enabled} vs {@code telegram.notification.action.enabled})
 * 켜도 Bean이 만들어지지 않는 상태였고, 로그도 에러도 남지 않았다.</p>
 *
 * <p>꺼졌을 때 Bean이 <b>없어야</b> 하는 것도 계약이다. 각 Feature의 sender가 이 Bean에
 * 의존하므로, 없으면 sender도 등록되지 않아 "발송 수단이 없으면 후보를 소진하지 않는다"는
 * Application 정책이 성립한다.</p>
 */
class TelegramSenderConfigTest {

    @EnableConfigurationProperties(TelegramProperties.class)
    static class PropertiesConfiguration {

        /** 연동 조회는 이 테스트의 관심사가 아니다 — Bean 등록 조건만 본다. */
        @Bean
        TelegramUserLinkRepository telegramUserLinkRepository() {
            return org.mockito.Mockito.mock(TelegramUserLinkRepository.class);
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class, TelegramSenderConfig.class)
            .withPropertyValues(
                    "telegram.bot.username=testbot",
                    "telegram.bot.token=dummy-token",
                    "telegram.link-token.ttl=PT10M"
            );

    @Test
    @DisplayName("발송이 켜져 있으면 발송 Bean이 등록된다.")
    void registersSenderWhenEnabled() {
        runner.withPropertyValues("telegram.notification.enabled=true")
                .run(context -> assertThat(context)
                        .hasSingleBean(TelegramNotificationService.class)
                        .hasSingleBean(TelegramMessageSender.class));
    }

    @Test
    @DisplayName("발송이 꺼져 있으면 발송 Bean을 등록하지 않는다.")
    void registersNothingWhenDisabled() {
        runner.withPropertyValues("telegram.notification.enabled=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(TelegramNotificationService.class)
                        .doesNotHaveBean(TelegramMessageSender.class));
    }

    /**
     * 타임아웃 블록은 생략할 수 있다 — 발송을 끄고 쓰는 환경에 설정을 강제하지 않기 위해서다.
     * {@code Bot}의 컴팩트 생성자가 기본값을 채운다.
     */
    @Test
    @DisplayName("타임아웃 설정을 생략해도 기동한다.")
    void startsWithoutTimeoutConfiguration() {
        runner.withPropertyValues("telegram.notification.enabled=false")
                .run(context -> assertThat(context).hasNotFailed());
    }
}
