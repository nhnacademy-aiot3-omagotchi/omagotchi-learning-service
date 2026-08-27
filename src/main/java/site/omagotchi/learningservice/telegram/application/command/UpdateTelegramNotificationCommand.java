package site.omagotchi.learningservice.telegram.application.command;

/**
 * Telegram 알림 수신 여부 변경 명령
 */
public record UpdateTelegramNotificationCommand(
        Boolean enabled
) {
}
