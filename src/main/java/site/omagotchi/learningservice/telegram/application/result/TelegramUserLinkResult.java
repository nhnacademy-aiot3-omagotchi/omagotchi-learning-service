package site.omagotchi.learningservice.telegram.application.result;

import site.omagotchi.learningservice.telegram.domain.TelegramUserLink;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TelegramUserLinkResult(
        UUID userId,
        Long telegramUserId,
        Long telegramChatId,
        Boolean notificationEnabled, // 알림 수신 여부
        OffsetDateTime linkedAt, // 연동 시각
        OffsetDateTime disconnectedAt // 연동 해제 시각
) {

    public static TelegramUserLinkResult from(TelegramUserLink link) {
        return new TelegramUserLinkResult(
                link.getUserId(),
                link.getTelegramUserId(),
                link.getTelegramChatId(),
                link.getNotificationEnabled(),
                link.getLinkedAt(),
                link.getDisconnectedAt()
        );
    }
}
