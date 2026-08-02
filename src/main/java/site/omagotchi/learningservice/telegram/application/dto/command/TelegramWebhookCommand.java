package site.omagotchi.learningservice.telegram.application.dto.command;

/**
 * Telegram Bot /start 메시지 처리 명령
 */
public record TelegramWebhookCommand(
        Long telegramUserId,
        Long telegramChatId,
        String text
) {
}
