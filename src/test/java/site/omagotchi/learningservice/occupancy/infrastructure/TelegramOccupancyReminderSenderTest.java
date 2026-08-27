package site.omagotchi.learningservice.occupancy.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.occupancy.application.port.OccupancyReminderSender;
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
 * 만료 임박 알림의 <b>문구</b>를 고정한다. 전송 자체는 {@code TelegramMessageSender}의 몫이다.
 */
@ExtendWith(MockitoExtension.class)
class TelegramOccupancyReminderSenderTest {

    private static final UUID OCCUPIER_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final OffsetDateTime EXPIRES_AT =
            OffsetDateTime.of(2026, 8, 13, 18, 30, 0, 0, ZoneOffset.ofHours(9));

    @Mock
    private TelegramNotificationService notificationService;

    @InjectMocks
    private TelegramOccupancyReminderSender sender;

    @Test
    @DisplayName("만료 임박 알림을 점유자에게 공간 이름과 종료 예정 시각으로 보낸다.")
    void sendsReminderToOccupierWithExpiryTime() {
        sender.sendExpiryReminder(reminder());

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(notificationService).send(eq(OCCUPIER_USER_ID), text.capture());
        assertThat(text.getValue()).contains("공간: 테스트 회의실")
                .contains("2026-08-13 18:30:00 (KST)");
    }

    /**
     * 건너뜀 여부는 {@code TelegramNotificationService}가 판정한다. 이 Class는 그 결과를
     * 그대로 전달할 뿐이다 — 호출부({@code OccupancyExpiryReminder})가 이 값으로
     * {@code reminder_sent_at} 기록 여부를 정한다.
     */
    @Test
    @DisplayName("수신자가 건너뛰어졌으면 false를 그대로 돌려준다.")
    void propagatesSkipFromNotificationService() {
        given(notificationService.send(any(), any())).willReturn(false);

        boolean sent = sender.sendExpiryReminder(reminder());

        assertThat(sent).isFalse();
    }

    @Test
    @DisplayName("실제로 발송됐으면 true를 그대로 돌려준다.")
    void propagatesSuccessFromNotificationService() {
        given(notificationService.send(any(), any())).willReturn(true);

        boolean sent = sender.sendExpiryReminder(reminder());

        assertThat(sent).isTrue();
    }

    private OccupancyReminderSender.ExpiryReminder reminder() {
        return new OccupancyReminderSender.ExpiryReminder(
                100L, 7L, "테스트 회의실", OCCUPIER_USER_ID, EXPIRES_AT);
    }
}
