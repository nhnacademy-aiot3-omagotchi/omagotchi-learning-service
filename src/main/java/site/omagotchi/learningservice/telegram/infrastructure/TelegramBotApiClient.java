package site.omagotchi.learningservice.telegram.infrastructure;

import jakarta.annotation.PreDestroy;
import org.apache.http.client.config.RequestConfig;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import site.omagotchi.learningservice.telegram.application.TelegramProperties;

import java.time.Duration;
import java.util.Objects;

/**
 * 텔레그램 HTTP 클라이언트
 */
public class TelegramBotApiClient extends DefaultAbsSender {

    public TelegramBotApiClient(TelegramProperties properties){
        super(botOptions(properties.bot()), requiredConfigured(properties.bot().token()));
    }

    @PreDestroy
    public void shutdown(){
        exe.shutdown();
    }

    private static DefaultBotOptions botOptions(TelegramProperties.Bot bot){
        TelegramProperties.Bot.Timeout timeout = bot.timeout();

        DefaultBotOptions options = new DefaultBotOptions();

        options.setMaxThreads(bot.maxThreads());
        options.setRequestConfig(RequestConfig.custom()
                .setConnectionRequestTimeout(millis(timeout.connectionRequest()))
                .setConnectTimeout(millis(timeout.connect()))
                .setSocketTimeout(millis(timeout.read()))
                .build());

        return options;

    }

    private static int millis(Duration duration){
        return (int) duration.toMillis();
    }

    private static String requiredConfigured(String token){
        if(Objects.isNull(token) || token.isBlank()){
            throw new IllegalArgumentException("telegram.bot.enabled=true이면 TELEGRAM_BOT_TOKEN이 필요합니다.");
        }

        return token;
    }
}
