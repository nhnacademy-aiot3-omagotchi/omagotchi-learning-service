package site.omagotchi.learningservice.telegram.presentation.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import site.omagotchi.learningservice.telegram.application.dto.command.TelegramWebhookCommand;

/**
 * Telegram Bot Webhook 요청
 */
public record TelegramWebhookRequest(
        @JsonProperty("update_id") Long updateId,
        Message message
) {

    public TelegramWebhookCommand toCommand() {
        if (message == null || message.chat() == null || message.from() == null) {
            return new TelegramWebhookCommand(null, null, null);
        }
        return new TelegramWebhookCommand(message.from().id(), message.chat().id(), message.text());
    }

    public record Message(
            Chat chat,
            From from,
            String text
    ) {
    }

    public record Chat(
            Long id,
            String type
    ) {
    }

    public record From(
            Long id,
            @JsonProperty("is_bot") Boolean bot,
            @JsonProperty("first_name") String firstName,
            String username
    ) {
    }
}
