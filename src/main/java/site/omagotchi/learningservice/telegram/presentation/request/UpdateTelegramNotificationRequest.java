package site.omagotchi.learningservice.telegram.presentation.request;

import jakarta.validation.constraints.NotNull;
import site.omagotchi.learningservice.telegram.application.command.UpdateTelegramNotificationCommand;

/**
 * Telegram 알림 수신 여부 변경 요청
 */
public record UpdateTelegramNotificationRequest(
        @NotNull Boolean enabled
) {

    public UpdateTelegramNotificationCommand toCommand() {
        return new UpdateTelegramNotificationCommand(enabled);
    }
}
