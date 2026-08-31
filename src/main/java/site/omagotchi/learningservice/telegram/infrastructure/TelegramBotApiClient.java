package site.omagotchi.learningservice.telegram.infrastructure;

import jakarta.annotation.PreDestroy;
import org.apache.http.client.config.RequestConfig;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import site.omagotchi.learningservice.telegram.application.TelegramProperties;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 텔레그램 HTTP 클라이언트
 */
public class TelegramBotApiClient extends DefaultAbsSender {

    public TelegramBotApiClient(TelegramProperties properties){
        super(botOptions(properties.bot()), properties.bot().token());
    }

    @PreDestroy
    public void shutdown(){
        exe.shutdown();

        try{
            if(!exe.awaitTermination(5, TimeUnit.SECONDS)){
                exe.shutdownNow();
            }
        }catch (InterruptedException e){
            exe.shutdownNow();
            Thread.currentThread().interrupt();
        }
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
}
