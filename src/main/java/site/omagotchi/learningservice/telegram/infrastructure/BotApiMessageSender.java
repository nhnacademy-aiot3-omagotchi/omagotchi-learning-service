package site.omagotchi.learningservice.telegram.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import site.omagotchi.learningservice.telegram.application.port.TelegramMessageSender;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/***
 * 텔레그램 메세지 발송자
 */
@Component
@RequiredArgsConstructor
public class BotApiMessageSender implements TelegramMessageSender {

    private final AbsSender sender;

    @Override
    public void send(Long chatId, String text){
        Message response;

        try{
            response = sender.execute(request(chatId, text));
        }catch (TelegramApiException e){
            throw new IllegalStateException("Telegram 발송에 실패했습니다. chatId=" + chatId, e);
        }

        requireDelivered(response, chatId);
    }

    /**
     * 비동기로 보내고 {@code timeout}까지만 기다린다.
     *
     * <p>동기 {@code execute}로는 이 계약을 지킬 수 없다 — {@code DefaultAbsSender}는
     * 요청별 {@code RequestConfig}를 열어 두지 않아(6.9.7.1에서 {@code configuredHttpPost}가
     * private) 호출마다 타임아웃을 바꿀 방법이 없다. 그래서 기다리는 쪽을 끊는다.</p>
     */
    @Override
    public void send(Long chatId, String text, Duration timeout){
        Message response;

        try{
            response = sender.executeAsync(request(chatId, text))
                    .get(Math.max(1L, timeout.toMillis()), TimeUnit.MILLISECONDS);
        }catch (TimeoutException e){
            throw new IllegalStateException(
                    "Telegram 발송이 제한 시간을 넘겼습니다. chatId=" + chatId, e);
        }catch (InterruptedException e){
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Telegram 발송이 중단됐습니다. chatId=" + chatId, e);
        }catch (ExecutionException | TelegramApiException e){
            throw new IllegalStateException("Telegram 발송에 실패했습니다. chatId=" + chatId, e);
        }

        requireDelivered(response, chatId);
    }

    private static void requireDelivered(Message response, Long chatId){
        if(Objects.isNull(response) || Objects.isNull(response.getMessageId())){
            throw new IllegalStateException("Telegram 발송 성공했지만 응답을 확인할 수 없습니다. chatId=" + chatId);
        }
    }

    private static SendMessage request(Long chatId, String text){
        return SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .build();
    }




}
