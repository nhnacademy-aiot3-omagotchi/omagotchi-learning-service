package site.omagotchi.learningservice.telegram.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import site.omagotchi.learningservice.telegram.application.TelegramProperties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 발송 수단이 <b>설정 없이는 만들어지지 않는다</b>는 계약을 고정한다.
 *
 * <p>예전에는 {@code @ConditionalOnProperty}로 발송을 켜고 껐고, 이 테스트는 그 조건 경로가
 * 설정과 어긋나지 않는지 감시했다. 조건이 사라진 지금 감시할 대상은 하나 위로 올라갔다 —
 * <b>필수 설정이 비어 있을 때 기동이 실패하는가</b>다.</p>
 *
 * <p>실패가 조용하다는 점은 그대로다. 토큰이 빈 문자열이어도 {@link TelegramBotApiClient}는
 * 아무 불평 없이 만들어지고, 룰 히트가 원래 드물어서 발송이 안 되는 것을 며칠 뒤에나
 * 알아챈다. 그래서 기동 시점에 막는다.</p>
 */
class TelegramSenderConfigTest {

    @EnableConfigurationProperties(TelegramProperties.class)
    static class PropertiesConfiguration { }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class, TelegramSenderConfig.class);

    private ApplicationContextRunner withProperties(String... overrides) {
        return runner.withPropertyValues(
                "telegram.bot.username=testbot",
                "telegram.bot.token=dummy-token",
                "telegram.webhook.secret=dummy-secret",
                "telegram.link-token.ttl=PT10M"
        ).withPropertyValues(overrides);
    }

    /**
     * 어댑터({@code BotApiMessageSender})는 {@code @Component}로 스스로 등록되므로 여기서
     * 확인하지 않는다 — 이 Config가 만드는 것은 클라이언트 하나뿐이다.
     */
    @Test
    @DisplayName("설정이 갖춰지면 봇 API 클라이언트가 등록된다.")
    void registersBotApiClient() {
        withProperties().run(context -> assertThat(context)
                .hasSingleBean(TelegramBotApiClient.class));
    }

    @Test
    @DisplayName("봇 토큰이 비면 기동에 실패한다.")
    void failsWhenBotTokenMissing() {
        withProperties("telegram.bot.token=").run(context -> assertThat(context)
                .hasFailed()
                .getFailure()
                .hasStackTraceContaining("TELEGRAM_BOT_TOKEN"));
    }

    @Test
    @DisplayName("웹훅 시크릿이 비면 기동에 실패한다.")
    void failsWhenWebhookSecretMissing() {
        withProperties("telegram.webhook.secret=").run(context -> assertThat(context)
                .hasFailed()
                .getFailure()
                .hasStackTraceContaining("TELEGRAM_WEBHOOK_SECRET"));
    }

    /**
     * 타임아웃 블록은 생략할 수 있다 — {@code Bot}의 컴팩트 생성자가 기본값을 채운다.
     * 필수와 선택을 가르는 선이 여기다.
     */
    @Test
    @DisplayName("타임아웃 설정을 생략해도 기동한다.")
    void startsWithoutTimeoutConfiguration() {
        withProperties().run(context -> assertThat(context).hasNotFailed());
    }
}
