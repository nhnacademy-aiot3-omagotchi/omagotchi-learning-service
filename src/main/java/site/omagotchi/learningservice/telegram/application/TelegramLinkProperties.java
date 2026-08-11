package site.omagotchi.learningservice.telegram.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Telegram 사용자 연동 링크 발급 정책이다.
 */
@Validated
@ConfigurationProperties(prefix = "telegram")
public record TelegramLinkProperties(
        @Valid
        @NotNull(message = "telegram.bot은 필수입니다.")
        Bot bot,

        @Valid
        @NotNull(message = "telegram.link-token은 필수입니다.")
        LinkToken linkToken
) {

    public record Bot(
            @NotBlank(message = "telegram.bot.username은 비어 있을 수 없습니다.")
            String username
    ) {
    }

    public record LinkToken(
            @NotNull(message = "telegram.link-token.ttl은 필수입니다.")
            @DurationMin(seconds = 1, message = "telegram.link-token.ttl은 1초 이상이어야 합니다.")
            Duration ttl
    ) {
    }
}
