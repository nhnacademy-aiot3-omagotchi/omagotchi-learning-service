package site.omagotchi.learningservice.telegram.application.dto.result;

import site.omagotchi.learningservice.telegram.domain.TelegramUserLink;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TelegramUserLinkResponse(
        UUID userId,
        Long telegramUserId,
        Long telegramChatId,
        Boolean notificationEnabled, // 알림 수신 여부
        OffsetDateTime linkedAt, // 연동 시각
        OffsetDateTime disconnectedAt // 연동 해제 시각
) {

    public static TelegramUserLinkResponse from(TelegramUserLink link) {
        return new TelegramUserLinkResponse(
                link.getUserId(),
                link.getTelegramUserId(),
                link.getTelegramChatId(),
                link.getNotificationEnabled(),
                link.getLinkedAt(),
                link.getDisconnectedAt()
        );
    }
}
