package site.omagotchi.learningservice.telegram.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import site.omagotchi.learningservice.telegram.application.TelegramProperties;

@Configuration
public class TelegramSenderConfig {

    @Bean
    public TelegramBotApiClient telegramBotApiSender(TelegramProperties properties){
        return new TelegramBotApiClient(properties);
    }

}
