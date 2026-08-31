package site.omagotchi.learningservice.occupancy.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.global.util.DateTimePolicy;
import site.omagotchi.learningservice.occupancy.application.port.VacancyAlertSender;
import site.omagotchi.learningservice.telegram.application.TelegramNotificationService;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * 공실 알림과 삭제 통보를 텔레그램으로 발송한다 (MR-03, RM-15).
 */
@Component
@RequiredArgsConstructor
public class TelegramVacancyAlertSender implements VacancyAlertSender {

    /**
     * 저장은 UTC 그대로 두고, 사람이 읽는 문구에서만 KST로 바꾼다.
     */
    private static final DateTimeFormatter DISPLAY_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss '('zzz')'", Locale.KOREA);

    private final TelegramNotificationService notificationService;

    @Override
    public boolean sendVacancyAlert(VacancyNotice notice) {
        Objects.requireNonNull(notice, "공실 알림은 필수입니다.");
        return notificationService.send(notice.recipientUserId(), messageOf(notice));
    }

    @Override
    public void sendDiscardNotice(DiscardNotice notice) {
        Objects.requireNonNull(notice, "삭제 통보는 필수입니다.");
        notificationService.send(notice.recipientUserId(), discardMessageOf(notice));
    }

    /**
     * 알림 본문.
     */
    private static String messageOf(VacancyNotice notice) {
        return """
                [회의실 공실 안내]

                공간: %s
                신청하신 회의실이 비었습니다.
                비워진 시각: %s

                먼저 점유하는 사람이 사용합니다.
                """.formatted(notice.spaceName(),
                notice.vacatedAt().atZoneSameInstant(DateTimePolicy.ZONE_ID).format(DISPLAY_FORMATTER))
                .stripTrailing();
    }

    /**
     * 삭제 통보 본문.
     */
    private static String discardMessageOf(DiscardNotice notice) {
        return """
                [공실 알림 신청 취소 안내]

                공간: %s
                해당 공간이 비활성화되어 신청하신 공실 알림이 취소되었습니다.
                취소 시각: %s
                """.formatted(notice.spaceName(),
                notice.discardedAt().atZoneSameInstant(DateTimePolicy.ZONE_ID).format(DISPLAY_FORMATTER))
                .stripTrailing();
    }
}
