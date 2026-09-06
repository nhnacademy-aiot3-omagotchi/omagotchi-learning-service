package site.omagotchi.learningservice.attendance.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.attendance.application.port.AttendanceReminderSender;
import site.omagotchi.learningservice.attendance.domain.ReminderType;
import site.omagotchi.learningservice.telegram.application.TelegramNotificationService;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("텔레그램 출결 알림 발송자")
class TelegramAttendanceReminderSenderTest {

    private static final UUID USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private TelegramNotificationService notificationService;

    @InjectMocks
    private TelegramAttendanceReminderSender sender;

    @Test
    @DisplayName("입실 알림에 기수와 KST 설정 시각을 표시한다")
    void formatsCheckInReminder() {
        sender.sendAsync(reminder(
                ReminderType.CHECK_IN_BEFORE_DEADLINE,
                OffsetDateTime.of(2026, 9, 5, 0, 0, 0, 0, ZoneOffset.UTC)
        ));

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(notificationService).sendAsync(eq(USER_ID), text.capture());
        assertThat(text.getValue())
                .contains("[입실 안내]")
                .contains("기수: AIoT 3기")
                .contains("입실 시각: 2026-09-05 09:00:00 (KST)");
    }

    @Test
    @DisplayName("퇴실 알림은 체크 누락 위험을 안내하고 마감이라고 표현하지 않는다")
    void formatsCheckOutReminder() {
        sender.sendAsync(reminder(
                ReminderType.CHECK_OUT_BEFORE_DEADLINE,
                OffsetDateTime.of(2026, 9, 5, 9, 0, 0, 0, ZoneOffset.UTC)
        ));

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(notificationService).sendAsync(eq(USER_ID), text.capture());
        assertThat(text.getValue())
                .contains("[퇴실 체크 안내]")
                .contains("퇴실 체크를 하지 않으면 출결이 확정되지 않습니다.")
                .contains("퇴실 시각: 2026-09-05 18:00:00 (KST)")
                .doesNotContain("마감");
    }

    @Test
    @DisplayName("텔레그램이 수신자를 건너뛴 결과를 그대로 반환한다")
    void propagatesSkippedDelivery() {
        given(notificationService.sendAsync(any(), any()))
                .willReturn(CompletableFuture.completedFuture(false));

        boolean sent = sender.sendAsync(reminder(
                ReminderType.CHECK_IN_BEFORE_DEADLINE,
                OffsetDateTime.now()
        )).toCompletableFuture().join();

        assertThat(sent).isFalse();
    }

    private AttendanceReminderSender.Reminder reminder(
            ReminderType type,
            OffsetDateTime scheduledAt
    ) {
        return new AttendanceReminderSender.Reminder(
                USER_ID,
                "AIoT 3기",
                type,
                scheduledAt
        );
    }
}
