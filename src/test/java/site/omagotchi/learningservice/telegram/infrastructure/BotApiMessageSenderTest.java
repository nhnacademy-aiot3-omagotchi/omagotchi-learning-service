package site.omagotchi.learningservice.telegram.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

/**
 * 전송 계약을 고정한다 — <b>정상 반환은 실제 발송 성공을 뜻한다.</b>
 *
 * <p>호출부가 그 반환을 근거로 소진 기록({@code notified_at}, {@code reminder_sent_at})을
 * 남기므로, 실패를 조용히 삼키면 <b>보내지 못한 알림이 보낸 것으로 기록된다.</b> 예전에는
 * 이 계약이 Feature별 sender 세 곳에 복제돼 있었고 지금은 여기 하나다.</p>
 */
@ExtendWith(MockitoExtension.class)
class BotApiMessageSenderTest {

    private static final Long CHAT_ID = 12345L;

    @Mock
    private AbsSender absSender;

    @Test
    @DisplayName("성공 응답을 받으면 지정 채팅으로 보낸 것으로 처리한다.")
    void sendsToGivenChat() throws Exception {
        given(absSender.execute(any(SendMessage.class))).willReturn(messageWithId());

        BotApiMessageSender sender = new BotApiMessageSender(absSender);
        assertThatCode(() -> sender.send(CHAT_ID, "본문")).doesNotThrowAnyException();

        ArgumentCaptor<SendMessage> request = ArgumentCaptor.forClass(SendMessage.class);
        verify(absSender).execute(request.capture());
        assertThat(request.getValue().getChatId()).isEqualTo(CHAT_ID.toString());
        assertThat(request.getValue().getText()).isEqualTo("본문");
    }

    @Test
    @DisplayName("Telegram API 오류는 정상 반환하지 않고 호출부로 전파한다.")
    void propagatesApiFailure() throws Exception {
        TelegramApiException apiException = new TelegramApiException("강제 Telegram 오류");
        willThrow(apiException).given(absSender).execute(any(SendMessage.class));

        BotApiMessageSender sender = new BotApiMessageSender(absSender);
        assertThatThrownBy(() -> sender.send(CHAT_ID, "본문"))
                .isInstanceOf(IllegalStateException.class)
                .hasCause(apiException);
    }

    /**
     * 응답은 왔는데 {@code messageId}가 없으면 발송을 확인할 수 없다. 성공으로 처리하면
     * 호출부가 소진 기록을 남겨 <b>재시도 기회를 잃는다.</b>
     */
    @Test
    @DisplayName("성공 응답을 확인할 수 없으면 발송 성공으로 처리하지 않는다.")
    void rejectsUnconfirmedResponse() throws Exception {
        given(absSender.execute(any(SendMessage.class))).willReturn(null);

        BotApiMessageSender sender = new BotApiMessageSender(absSender);
        assertThatThrownBy(() -> sender.send(CHAT_ID, "본문"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("확인할 수 없");
    }

    private static Message messageWithId() {
        Message message = new Message();
        message.setMessageId(1);
        return message;
    }
}
