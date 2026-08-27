package site.omagotchi.learningservice.telegram.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import site.omagotchi.learningservice.telegram.application.TelegramNotificationService;
import site.omagotchi.learningservice.telegram.application.TelegramProperties;

@Configuration
@ConditionalOnProperty(prefix = "telegram.notification", name = "enabled", havingValue = "true")
public class TelegramSenderConfig {

    @Bean
    public TelegramBotApiClient telegramBotApiSender(TelegramProperties properties){
        return new TelegramBotApiClient(properties);
    }

    @Bean
    public TelegramMessageSender telegramMessageSender(TelegramBotApiClient sender){
        return new TelegramMessageSender(sender);
    }

    /**
     * 다른 Feature가 쓰는 발송 진입점. 발송 Bean과 생명주기를 맞춘다
     * 발송을 끄면 sender가 사라져야 함
     */
    @Bean
    public TelegramNotificationService telegramNotificationService(
            TelegramUserLinkRepository userLinkRepository,
            TelegramMessageSender messageSender
    ){
        return new TelegramNotificationService(userLinkRepository, messageSender);
    }
}
