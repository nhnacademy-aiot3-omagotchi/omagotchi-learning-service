package site.omagotchi.learningservice.environment.infrastructure;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.environment.application.port.ActionNotificationSender;
import site.omagotchi.learningservice.global.util.DateTimePolicy;
import site.omagotchi.learningservice.rule.domain.Operator;
import site.omagotchi.learningservice.telegram.application.TelegramNotificationService;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Slf4j
@RequiredArgsConstructor
@Component
public class TelegramActionNotificationSender implements ActionNotificationSender {

    private static final DateTimeFormatter DISPLAY_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss '('zzz')'", Locale.KOREA);

    private final TelegramNotificationService notificationService;

    @Override
    public boolean send(ActionNotice notice) {
        boolean success = notificationService.send(notice. recipientUserId(), messageOf(notice));

        if(!success){
            log.info("텔레그램을 연동하지 않은 관리자입니다. 건너뜁니다. userId={}", notice.recipientUserId());
        }

        return success;
    }

    private static String messageOf(ActionNotice notice) {
        String body = """
                [자동 조치 완료]

                위치: %s
                측정: %s %s (기준 %s %s)
                조치: %s
                확인: %s"""
                .formatted(
                        notice.location(),
                        notice.measurement(),
                        notice.value(),
                        notice.threshold(),
                        directionOf(notice.operator()),
                        notice.action().label(),
                        notice.confirmedAt().atZone(DateTimePolicy.ZONE_ID).format(DISPLAY_FORMATTER)
                );

        // 실제 장치가 아닌데 "환기 완료"만 오면 받는 사람이 오해한다(§3 계약③)
        return notice.simulated() ? body + "\n\n※ 시뮬레이터 응답입니다" : body;
    }

    private static String directionOf(Operator operator) {
        return (operator == Operator.GT || operator == Operator.GTE) ? "초과" : "미만";
    }
}

