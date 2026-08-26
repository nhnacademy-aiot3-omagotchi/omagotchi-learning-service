package site.omagotchi.learningservice.team.infrastructure;

import org.apache.http.client.config.RequestConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import site.omagotchi.learningservice.global.util.DateTimePolicy;
import site.omagotchi.learningservice.team.application.port.TeamNotificationSender;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * 고정된 테스트용 Telegram 개인 채팅으로 팀 해체 통보를 발송한다 (GR-19).
 *
 * <p>Telegram API의 동기 응답을 확인한 뒤에만 정상 반환한다. 오류를 예외로 전파해야
 * 호출부가 실패로 기록한다 — {@code TelegramVacancyAlertSender}와 같은 계약이다.</p>
 *
 * <p><b>{@code recipientUserId}를 쓰지 않는다.</b> 계정별 채팅 매핑이 아직 없어 모든
 * (구)팀원의 통보가 같은 테스트 채팅으로 간다. 발송 경로가 실제로 동작하는지 확인하기 위한
 * 임시 구현이며, 계정별 발송은 {@code telegram_user_links}가 붙은 뒤에 온다.</p>
 *
 * <p><b>점유의 Telegram sender와 전송 Code가 겹치는 것은 의도된 임시 상태다.</b> 지금
 * 공통 부분을 뽑으면 계정별 발송으로 바뀔 때 그 추상이 먼저 깨진다. 실제 발송 수단이
 * 확정되면 그때 함께 정리한다.</p>
 *
 * <p>활성화 설정이 {@code true}일 때만 Bean이 등록된다. 비활성 상태에서는 no-op Bean을
 * 만들지 않아, 실제 sender가 없을 때 통보를 시도하지 않는 Application 정책을 유지한다.</p>
 */
@Component
@ConditionalOnProperty(
        prefix = "omagotchi.team.telegram",
        name = "enabled",
        havingValue = "true"
)
public class TelegramTeamNotificationSender implements TeamNotificationSender {

    private static final int CONNECTION_REQUEST_TIMEOUT_MILLIS = 5_000;
    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;
    private static final int SOCKET_TIMEOUT_MILLIS = 10_000;

    /**
     * 저장은 UTC 그대로 두고, 사람이 읽는 문구에서만 KST로 바꾼다.
     *
     * <p>표기를 {@code zzz}로 <b>파생</b>시키는 것이 의도다. "(KST)"를 문자열로 박으면
     * {@link DateTimePolicy#ZONE_ID}가 바뀌었을 때 시각만 따라 바뀌고 라벨은 그대로 남아,
     * <b>틀린 시간대를 단언하는 문구</b>가 된다.</p>
     */
    private static final DateTimeFormatter DISPLAY_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss '('zzz')'", Locale.KOREA);

    private final AbsSender telegramSender;
    private final String chatId;

    @Autowired
    public TelegramTeamNotificationSender(
            @Value("${omagotchi.team.telegram.bot-token}") String botToken,
            @Value("${omagotchi.team.telegram.chat-id}") String chatId
    ) {
        this(new TelegramBotApiSender(requireConfigured(botToken, "TELEGRAM_BOT_TOKEN")), chatId);
    }

    TelegramTeamNotificationSender(AbsSender telegramSender, String chatId) {
        this.telegramSender = Objects.requireNonNull(telegramSender, "Telegram sender는 필수입니다.");
        this.chatId = requireConfigured(chatId, "TELEGRAM_CHAT_ID");
    }

    @Override
    public void sendDisbandNotice(DisbandNotice notice) {
        Objects.requireNonNull(notice, "해체 통보는 필수입니다.");
        send(disbandMessageOf(notice), "Telegram 팀 해체 통보 발송에 실패했습니다.");
    }

    /**
     * 실제 전송.
     *
     * <p>동기 응답을 확인한 뒤에만 정상 반환한다 — 호출부가 그 반환을 <b>실제 발송 성공</b>
     * 으로 읽고 건수를 집계하기 때문이다.</p>
     */
    private void send(String text, String failureMessage) {
        SendMessage request = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .build();

        Message response;
        try {
            response = telegramSender.execute(request);
        } catch (TelegramApiException exception) {
            throw new IllegalStateException(failureMessage, exception);
        }

        if (response == null || response.getMessageId() == null) {
            throw new IllegalStateException("Telegram 발송 성공 응답을 확인할 수 없습니다.");
        }
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

    private static String requireConfigured(String value, String environmentVariable) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(environmentVariable + " 환경변수는 비어 있을 수 없습니다.");
        }
        return value;
    }

    private static final class TelegramBotApiSender extends DefaultAbsSender {

        private TelegramBotApiSender(String botToken) {
            super(botOptions(), botToken);
        }

        private static DefaultBotOptions botOptions() {
            DefaultBotOptions options = new DefaultBotOptions();
            options.setRequestConfig(RequestConfig.custom()
                    .setConnectionRequestTimeout(CONNECTION_REQUEST_TIMEOUT_MILLIS)
                    .setConnectTimeout(CONNECT_TIMEOUT_MILLIS)
                    .setSocketTimeout(SOCKET_TIMEOUT_MILLIS)
                    .build());
            return options;
        }
    }
}
