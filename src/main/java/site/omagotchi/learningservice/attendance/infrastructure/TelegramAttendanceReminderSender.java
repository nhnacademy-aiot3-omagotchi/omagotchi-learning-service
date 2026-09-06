package site.omagotchi.learningservice.attendance.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.attendance.application.port.AttendanceReminderSender;
import site.omagotchi.learningservice.global.util.DateTimePolicy;
import site.omagotchi.learningservice.telegram.application.TelegramNotificationService;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/** 출결 알림 문구를 만들고 텔레그램 발송 진입점에 위임한다. */
@Component
@RequiredArgsConstructor
public class TelegramAttendanceReminderSender implements AttendanceReminderSender {

    private static final DateTimeFormatter DISPLAY_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss '('zzz')'", Locale.KOREA);

    private final TelegramNotificationService notificationService;

    @Override
    public CompletionStage<Boolean> sendAsync(Reminder reminder) {
        Objects.requireNonNull(reminder, "출결 알림은 필수입니다.");
        return notificationService.sendAsync(reminder.recipientUserId(), messageOf(reminder));
    }

    private static String messageOf(Reminder reminder) {
        String scheduledAt = reminder.scheduledAt()
                .atZoneSameInstant(DateTimePolicy.ZONE_ID)
                .format(DISPLAY_FORMATTER);

        return switch (reminder.type()) {
            case CHECK_IN_BEFORE_DEADLINE -> """
                    [입실 안내]

                    기수: %s
                    입실 시각까지 얼마 남지 않았습니다.
                    입실 시각: %s
                    """.formatted(reminder.cohortName(), scheduledAt).stripTrailing();
            case CHECK_OUT_BEFORE_DEADLINE -> """
                    [퇴실 체크 안내]

                    기수: %s
                    퇴실 시각까지 얼마 남지 않았습니다.
                    퇴실 체크를 하지 않으면 출결이 확정되지 않습니다.
                    퇴실 시각: %s
                    """.formatted(reminder.cohortName(), scheduledAt).stripTrailing();
        };
    }
}
