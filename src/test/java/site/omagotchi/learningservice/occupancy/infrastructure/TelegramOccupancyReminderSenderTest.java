package site.omagotchi.learningservice.occupancy.infrastructure;

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
import site.omagotchi.learningservice.occupancy.application.port.OccupancyReminderSender;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TelegramOccupancyReminderSenderTest {

    private static final String TEST_CHAT_ID = "test-occupancy-chat";
    private static final OffsetDateTime EXPIRES_AT =
            OffsetDateTime.of(2026, 8, 13, 18, 30, 0, 0, ZoneOffset.ofHours(9));

    @Mock
    private AbsSender telegramSender;

    @Test
    @DisplayName("Telegram 성공 응답을 받으면 지정 chat ID와 만료 시각으로 동기 발송한다.")
    void sendsToConfiguredChatWithExpiryTime() throws Exception {
        TelegramOccupancyReminderSender sender =
                new TelegramOccupancyReminderSender(telegramSender, TEST_CHAT_ID);
        Message response = new Message();
        response.setMessageId(1);
        given(telegramSender.execute(any(SendMessage.class))).willReturn(response);

        assertThatCode(() -> sender.sendExpiryReminder(reminder())).doesNotThrowAnyException();

        ArgumentCaptor<SendMessage> request = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramSender).execute(request.capture());
        assertThat(request.getValue().getChatId()).isEqualTo(TEST_CHAT_ID);
        assertThat(request.getValue().getText())
                .contains("공간: 테스트 회의실")
                .contains("2026-08-13 18:30:00");
    }

    @Test
    @DisplayName("Telegram API 오류는 정상 반환하지 않고 호출부로 전파한다.")
    void propagatesTelegramApiFailure() throws Exception {
        TelegramOccupancyReminderSender sender =
                new TelegramOccupancyReminderSender(telegramSender, TEST_CHAT_ID);
        TelegramApiException apiException = new TelegramApiException("강제 Telegram 오류");
        willThrow(apiException).given(telegramSender).execute(any(SendMessage.class));

        assertThatThrownBy(() -> sender.sendExpiryReminder(reminder()))
                .isInstanceOf(IllegalStateException.class)
                .hasCause(apiException);
    }

    @Test
    @DisplayName("성공 Message를 확인할 수 없으면 발송 성공으로 처리하지 않는다.")
    void rejectsUnconfirmedSuccessResponse() throws Exception {
        TelegramOccupancyReminderSender sender =
                new TelegramOccupancyReminderSender(telegramSender, TEST_CHAT_ID);
        given(telegramSender.execute(any(SendMessage.class))).willReturn(null);

        assertThatThrownBy(() -> sender.sendExpiryReminder(reminder()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("성공 응답");
    }

    private OccupancyReminderSender.ExpiryReminder reminder() {
        return new OccupancyReminderSender.ExpiryReminder(
                100L,
                7L,
                "테스트 회의실",
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                EXPIRES_AT
        );
    }
}
