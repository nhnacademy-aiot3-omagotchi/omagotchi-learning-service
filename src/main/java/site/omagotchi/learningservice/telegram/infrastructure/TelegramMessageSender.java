package site.omagotchi.learningservice.telegram.infrastructure;

import lombok.RequiredArgsConstructor;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/***
 * 텔레그램 메세지 발송자
 */
@RequiredArgsConstructor
public class TelegramMessageSender {
    private final AbsSender sender;

    public void send(Long chatId, String text){
        Message response;

        try{
            response = sender.execute(request(chatId, text));
        }catch (TelegramApiException e){
            throw new IllegalStateException("Telegram 발송에 실패했습니다. chatId=" + chatId, e);
        }

        if(Objects.isNull(response) || Objects.isNull(response.getMessageId())){
            throw new IllegalStateException("Telegram 발송 성공했지만 응답을 확인할 수 없습니다. chatId=" + chatId);
        }
    }

    public CompletableFuture<Boolean> sendAsync(Long chatId, String text){
        try{
            return sender.executeAsync(request(chatId, text))
                    .thenApply(message -> message != null && message.getMessageId() != null);
        }catch (TelegramApiException e){
            return CompletableFuture.failedFuture(e);
        }
    }

    private static SendMessage request(Long chatId, String text){
        return SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .build();
    }




}
