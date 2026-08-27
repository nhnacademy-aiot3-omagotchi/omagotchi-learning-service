package site.omagotchi.learningservice.telegram.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
}
