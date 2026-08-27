package site.omagotchi.learningservice.telegram.application.port;

public interface TelegramMessageSender {
    void send(Long chatId, String text);
}
