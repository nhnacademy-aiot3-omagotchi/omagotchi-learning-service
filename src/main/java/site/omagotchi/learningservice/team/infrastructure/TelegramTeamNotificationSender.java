package site.omagotchi.learningservice.team.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.global.util.DateTimePolicy;
import site.omagotchi.learningservice.team.application.port.TeamNotificationSender;
import site.omagotchi.learningservice.telegram.application.TelegramNotificationService;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * 팀 해체 통보를 텔레그램으로 발송한다 (GR-19).
 */
@Component
@RequiredArgsConstructor
public class TelegramTeamNotificationSender implements TeamNotificationSender {

    private static final DateTimeFormatter DISPLAY_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss '('zzz')'", Locale.KOREA);

    private final TelegramNotificationService notificationService;

    @Override
    public void sendDisbandNotice(DisbandNotice notice) {
        Objects.requireNonNull(notice, "해체 통보는 필수입니다.");
        notificationService.send(notice.recipientUserId(), disbandMessageOf(notice));
    }

    /**
     * 해체 통보 본문.
     *
     * <p>팀 이름을 반드시 적는다. 사용자가 여러 기수에 속할 수 있어, 이름이 없으면 어느
     * 팀이 사라졌는지 알 수 없다.</p>
     *
     * <p>누가 해체했는지는 적지 않는다 — 통보의 목적은 "내 팀이 사라졌다"를 알리는 것이고,
     * 행위자를 밝히면 팀원 사이의 문제로 번질 수 있다.</p>
     */
    private static String disbandMessageOf(DisbandNotice notice) {
        return """
                [팀 해체 안내]

                팀: %s
                소속되어 있던 팀이 해체되었습니다.
                해체 시각: %s

                새로운 팀을 만들거나 다른 팀에 참여할 수 있습니다.
                """.formatted(notice.teamName(),
                notice.disbandedAt().atZoneSameInstant(DateTimePolicy.ZONE_ID).format(DISPLAY_FORMATTER))
                .stripTrailing();
    }
}
