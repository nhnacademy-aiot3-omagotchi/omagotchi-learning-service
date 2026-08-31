package site.omagotchi.learningservice.occupancy.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.global.util.DateTimePolicy;
import site.omagotchi.learningservice.occupancy.application.port.OccupancyReminderSender;
import site.omagotchi.learningservice.telegram.application.TelegramNotificationService;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * 점유 만료 임박 알림을 텔레그램으로 발송한다 (MR-16).
 */
@Component
@RequiredArgsConstructor
public class TelegramOccupancyReminderSender implements OccupancyReminderSender {

    /**
     * 저장은 UTC 그대로 두고, 사람이 읽는 문구에서만 KST로 바꾼다.
     */
    private static final DateTimeFormatter DISPLAY_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss '('zzz')'", Locale.KOREA);

    private final TelegramNotificationService notificationService;

    @Override
    public boolean sendExpiryReminder(ExpiryReminder reminder) {
        Objects.requireNonNull(reminder, "만료 임박 알림은 필수입니다.");
        return notificationService.send(reminder.occupierUserId(), messageOf(reminder));
    }

    private static String messageOf(ExpiryReminder reminder) {
        return """
                [회의실 이용 종료 안내]

                공간: %s
                이용 시간이 곧 종료됩니다.
                종료 예정 시각: %s
                """.formatted(reminder.spaceName(),
                reminder.expiresAt().atZoneSameInstant(DateTimePolicy.ZONE_ID).format(DISPLAY_FORMATTER))
                .stripTrailing();
    }
}
