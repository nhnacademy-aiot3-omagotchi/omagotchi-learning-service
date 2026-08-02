package site.omagotchi.learningservice.telegram.application.dto.result;

import java.time.OffsetDateTime;

public record TelegramLinkTokenResponse(
        String linkUrl,
        OffsetDateTime expiresAt
) {
}
