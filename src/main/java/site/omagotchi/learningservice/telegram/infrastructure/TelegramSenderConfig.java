package site.omagotchi.learningservice.telegram.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import site.omagotchi.learningservice.telegram.application.TelegramProperties;

/**
 * 텔레그램 HTTP 발송 수단을 조립한다.
 *
 * <p>인프라 Bean만 만든다 — {@code TelegramNotificationService}는 {@code @Service}로
 * 스스로 등록된다. 여기서 함께 만들면 컴포넌트 스캔과 이름이 겹쳐
 * {@code BeanDefinitionOverrideException}이 난다.</p>
 */
@Configuration
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
