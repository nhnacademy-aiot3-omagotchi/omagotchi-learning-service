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

import java.time.Duration;
import java.util.concurrent.CompletableFuture;

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

    // ────────────────────────────── 제한 시간 발송 ──────────────────────────────

    /**
     * 조치 알림은 MQ 리스너 위에서 돈다. <b>발송이 늦어져도 호출 스레드가 제한 시간보다
     * 오래 묶이면 안 된다</b> — 그 보장이 이 메서드의 존재 이유다.
     *
     * <p>동기 {@code execute}로는 지킬 수 없다. {@code DefaultAbsSender}가 요청별
     * {@code RequestConfig}를 열어 두지 않아 호출마다 타임아웃을 바꿀 수 없어서,
     * 기다리는 쪽을 끊는다.</p>
     */
    @Test
    @DisplayName("제한 시간을 넘기면 기다리지 않고 실패로 끝낸다.")
    void failsWhenSendExceedsTimeout() throws Exception {
        // 영원히 완료되지 않는 응답 — 텔레그램이 늦는 상황이다.
        given(absSender.executeAsync(any(SendMessage.class))).willReturn(new CompletableFuture<>());

        BotApiMessageSender sender = new BotApiMessageSender(absSender);

        long startedAt = System.nanoTime();
        assertThatThrownBy(() -> sender.send(CHAT_ID, "본문", Duration.ofMillis(50)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("제한 시간");

        // 응답을 끝까지 기다렸다면 여기서 멈춰 있었을 것이다.
        assertThat(System.nanoTime() - startedAt).isLessThan(Duration.ofSeconds(5).toNanos());
    }

    @Test
    @DisplayName("제한 시간 안에 성공 응답을 받으면 정상 반환한다.")
    void sendsWithinTimeout() throws Exception {
        given(absSender.executeAsync(any(SendMessage.class)))
                .willReturn(CompletableFuture.completedFuture(messageWithId()));

        BotApiMessageSender sender = new BotApiMessageSender(absSender);

        assertThatCode(() -> sender.send(CHAT_ID, "본문", Duration.ofSeconds(5)))
                .doesNotThrowAnyException();
    }

    /** 메시지 식별자가 없으면 성공으로 볼 수 없다. 동기 발송과 같은 판정이다. */
    @Test
    @DisplayName("제한 시간 발송도 응답을 확인할 수 없으면 실패로 본다.")
    void failsWhenTimedSendResponseIsUnverifiable() throws Exception {
        given(absSender.executeAsync(any(SendMessage.class)))
                .willReturn(CompletableFuture.completedFuture(new Message()));

        BotApiMessageSender sender = new BotApiMessageSender(absSender);

        assertThatThrownBy(() -> sender.send(CHAT_ID, "본문", Duration.ofSeconds(5)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("응답을 확인할 수 없습니다");
    }

    // ────────────────────────────── 기본 발송 ──────────────────────────────

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
