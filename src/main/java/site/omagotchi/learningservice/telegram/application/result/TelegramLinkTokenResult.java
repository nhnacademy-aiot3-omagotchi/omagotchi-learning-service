package site.omagotchi.learningservice.telegram.application.result;

import java.time.OffsetDateTime;

public record TelegramLinkTokenResult(
        String linkUrl,
        OffsetDateTime expiresAt
) {
}
