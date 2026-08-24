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
import site.omagotchi.learningservice.occupancy.application.port.VacancyAlertSender;

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
class TelegramVacancyAlertSenderTest {

    private static final String TEST_CHAT_ID = "test-vacancy-chat";
    private static final UUID RECIPIENT_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final OffsetDateTime VACATED_AT =
            OffsetDateTime.of(2026, 8, 13, 18, 30, 0, 0, ZoneOffset.ofHours(9));

    @Mock
    private AbsSender telegramSender;

    @Test
    @DisplayName("Telegram 성공 응답을 받으면 지정 chat ID와 공실 시각으로 동기 발송한다.")
    void sendsToConfiguredChatWithVacatedTime() throws Exception {
        TelegramVacancyAlertSender sender =
                new TelegramVacancyAlertSender(telegramSender, TEST_CHAT_ID);
        Message response = new Message();
        response.setMessageId(1);
        given(telegramSender.execute(any(SendMessage.class))).willReturn(response);

        assertThatCode(() -> sender.sendVacancyAlert(notice())).doesNotThrowAnyException();

        ArgumentCaptor<SendMessage> request = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramSender).execute(request.capture());
        assertThat(request.getValue().getChatId()).isEqualTo(TEST_CHAT_ID);
        assertThat(request.getValue().getText())
                .contains("공간: 테스트 회의실")
                .contains("2026-08-13 18:30:00 (KST)");
    }

    /**
     * 회의실은 공유 자원이라 신청자와 점유자의 기수가 다를 수 있다 (MR-34). 본문에
     * 사람이 들어가면 타 기수 사용자의 개인정보가 그대로 노출된다 (MR-36).
     */
    @Test
    @DisplayName("본문에 사람을 식별할 정보를 담지 않는다.")
    void messageCarriesNoPersonalInformation() throws Exception {
        TelegramVacancyAlertSender sender =
                new TelegramVacancyAlertSender(telegramSender, TEST_CHAT_ID);
        Message response = new Message();
        response.setMessageId(1);
        given(telegramSender.execute(any(SendMessage.class))).willReturn(response);

        sender.sendVacancyAlert(notice());

        ArgumentCaptor<SendMessage> request = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramSender).execute(request.capture());
        assertThat(request.getValue().getText())
                .doesNotContain(RECIPIENT_USER_ID.toString());
    }

    /** 알림은 사용 권한을 보장하지 않는다 (MR-04). 적지 않으면 이미 점유된 방을 보게 된다. */
    @Test
    @DisplayName("선착순임을 본문에 함께 알린다.")
    void messageStatesFirstComeFirstServed() throws Exception {
        TelegramVacancyAlertSender sender =
                new TelegramVacancyAlertSender(telegramSender, TEST_CHAT_ID);
        Message response = new Message();
        response.setMessageId(1);
        given(telegramSender.execute(any(SendMessage.class))).willReturn(response);

        sender.sendVacancyAlert(notice());

        ArgumentCaptor<SendMessage> request = ArgumentCaptor.forClass(SendMessage.class);
        verify(telegramSender).execute(request.capture());
        assertThat(request.getValue().getText()).contains("먼저 점유하는 사람");
    }

    @Test
    @DisplayName("Telegram API 오류는 정상 반환하지 않고 호출부로 전파한다.")
    void propagatesTelegramApiFailure() throws Exception {
        TelegramVacancyAlertSender sender =
                new TelegramVacancyAlertSender(telegramSender, TEST_CHAT_ID);
        TelegramApiException apiException = new TelegramApiException("강제 Telegram 오류");
        willThrow(apiException).given(telegramSender).execute(any(SendMessage.class));

        assertThatThrownBy(() -> sender.sendVacancyAlert(notice()))
                .isInstanceOf(IllegalStateException.class)
                .hasCause(apiException);
    }

    @Test
    @DisplayName("성공 Message를 확인할 수 없으면 발송 성공으로 처리하지 않는다.")
    void rejectsUnconfirmedSuccessResponse() throws Exception {
        TelegramVacancyAlertSender sender =
                new TelegramVacancyAlertSender(telegramSender, TEST_CHAT_ID);
        given(telegramSender.execute(any(SendMessage.class))).willReturn(null);

        assertThatThrownBy(() -> sender.sendVacancyAlert(notice()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("성공 응답");
    }

    private VacancyAlertSender.VacancyNotice notice() {
        return new VacancyAlertSender.VacancyNotice(500L, 7L, "테스트 회의실", RECIPIENT_USER_ID, VACATED_AT);
    }
}
