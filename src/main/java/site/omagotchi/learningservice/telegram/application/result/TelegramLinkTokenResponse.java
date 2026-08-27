package site.omagotchi.learningservice.telegram.application.result;

import java.time.OffsetDateTime;

public record TelegramLinkTokenResponse(
        String linkUrl,
        OffsetDateTime expiresAt
) {
}
