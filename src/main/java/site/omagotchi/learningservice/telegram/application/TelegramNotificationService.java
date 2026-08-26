package site.omagotchi.learningservice.telegram.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.omagotchi.learningservice.telegram.infrastructure.TelegramMessageSender;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TelegramNotificationService {
    private final TelegramRecipientService recipientService;
    private final Optional<TelegramMessageSender> messageSender;

    public boolean send(UUID userId, String text) {
        if (messageSender.isEmpty()) {
            return false;
        }

        Optional<Long> chatId = recipientService.findChatId(userId);

        if (chatId.isEmpty()) {
            return false;
        }

        messageSender.get().send(chatId.get(), text);

        return true;
    }
}
