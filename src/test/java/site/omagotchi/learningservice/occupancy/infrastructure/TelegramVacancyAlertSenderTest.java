package site.omagotchi.learningservice.occupancy.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.occupancy.application.port.VacancyAlertSender;
import site.omagotchi.learningservice.telegram.application.TelegramNotificationService;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * 공실 알림·삭제 통보의 문구를 고정한다.
 */
@ExtendWith(MockitoExtension.class)
class TelegramVacancyAlertSenderTest {

    private static final UUID RECIPIENT_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final OffsetDateTime VACATED_AT =
            OffsetDateTime.of(2026, 8, 13, 18, 30, 0, 0, ZoneOffset.ofHours(9));

    @Mock
    private TelegramNotificationService notificationService;

    @InjectMocks
    private TelegramVacancyAlertSender sender;

    @Test
    @DisplayName("공실 알림을 신청자에게 공간 이름과 공실 시각으로 보낸다.")
    void sendsVacancyAlertToRecipientWithVacatedTime() {
        sender.sendVacancyAlert(notice());

        assertThat(capturedText()).contains("공간: 테스트 회의실")
                .contains("2026-08-13 18:30:00 (KST)");
    }

    /**
     * 회의실은 공유 자원이라 신청자와 점유자의 기수가 다를 수 있다 (MR-34). 본문에
     * 사람이 들어가면 타 기수 사용자의 개인정보가 그대로 노출된다 (MR-36).
     */
    @Test
    @DisplayName("본문에 사람을 식별할 정보를 담지 않는다.")
    void messageCarriesNoPersonalInformation() {
        sender.sendVacancyAlert(notice());

        assertThat(capturedText()).doesNotContain(RECIPIENT_USER_ID.toString());
    }

    /** 알림은 사용 권한을 보장하지 않는다 (MR-04). 적지 않으면 이미 점유된 방을 보게 된다. */
    @Test
    @DisplayName("선착순임을 본문에 함께 알린다.")
    void messageStatesFirstComeFirstServed() {
        sender.sendVacancyAlert(notice());

        assertThat(capturedText()).contains("먼저 점유하는 사람");
    }

    /**
     * 삭제 통보는 <b>왜 사라졌는지</b>를 반드시 적는다. 이유가 없으면 사용자는 다시 신청하려다
     * 400을 받는다 — 비활성 공간에는 활성 점유가 있을 수 없기 때문이다.
     */
    @Test
    @DisplayName("삭제 통보에 비활성화가 사유임을 적는다.")
    void discardNoticeStatesReason() {
        sender.sendDiscardNotice(new VacancyAlertSender.DiscardNotice(
                7L, "테스트 회의실", RECIPIENT_USER_ID, VACATED_AT));

        assertThat(capturedText()).contains("비활성화")
                .contains("공간: 테스트 회의실");
    }

    /**
     * 건너뜀 여부는 {@code TelegramNotificationService}가 판정한다. 이 Class는 그 결과를
     * 그대로 전달할 뿐이다 — 호출부({@code VacancyAlertDelivery})가 이 값으로 신청 소진
     * 여부를 정한다.
     */
    @Test
    @DisplayName("수신자가 건너뛰어졌으면 false를 그대로 돌려준다.")
    void propagatesSkipFromNotificationService() {
        given(notificationService.send(any(), any())).willReturn(false);

        boolean sent = sender.sendVacancyAlert(notice());

        assertThat(sent).isFalse();
    }

    @Test
    @DisplayName("실제로 발송됐으면 true를 그대로 돌려준다.")
    void propagatesSuccessFromNotificationService() {
        given(notificationService.send(any(), any())).willReturn(true);

        boolean sent = sender.sendVacancyAlert(notice());

        assertThat(sent).isTrue();
    }

    private String capturedText() {
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(notificationService).send(eq(RECIPIENT_USER_ID), text.capture());
        return text.getValue();
    }

    private VacancyAlertSender.VacancyNotice notice() {
        return new VacancyAlertSender.VacancyNotice(500L, 7L, "테스트 회의실", RECIPIENT_USER_ID, VACATED_AT);
    }
}
