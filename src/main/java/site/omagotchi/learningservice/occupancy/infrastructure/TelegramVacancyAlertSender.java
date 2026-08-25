package site.omagotchi.learningservice.occupancy.infrastructure;

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
import site.omagotchi.learningservice.occupancy.application.port.VacancyAlertSender;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * 고정된 테스트용 Telegram 개인 채팅으로 공실 알림을 발송한다 (MR-03).
 *
 * <p>Telegram API의 동기 응답을 확인한 뒤에만 정상 반환한다. 오류를 예외로 전파해야
 * 호출부가 {@code notified_at}을 기록하지 않고 대기 상태로 남긴다 —
 * {@link TelegramOccupancyReminderSender}와 같은 계약이다.</p>
 *
 * <p><b>{@code recipientUserId}를 쓰지 않는다.</b> 계정별 채팅 매핑이 아직 없어 모든
 * 신청자의 알림이 같은 테스트 채팅으로 간다. 발송 경로가 실제로 동작하는지 확인하기 위한
 * 임시 구현이며, 계정별 발송은 {@code telegram_user_links}가 붙은 뒤에 온다.</p>
 *
 * <p><b>{@link TelegramOccupancyReminderSender}와 전송 코드가 겹치는 것은 의도된 임시
 * 상태다.</b> 지금 공통 부분을 뽑으면 계정별 발송으로 바뀔 때 그 추상이 먼저 깨진다 —
 * 두 알림의 수신자 결정 방식이 다르기 때문이다(점유자 1명 vs 신청자 N명). 실제 발송
 * 수단이 확정되면 그때 함께 정리한다.</p>
 *
 * <p>활성화 설정이 {@code true}일 때만 Bean이 등록된다. 비활성 상태에서는 no-op Bean을
 * 만들지 않아, 실제 sender가 없을 때 신청을 소진하지 않는 Application 정책을 유지한다.</p>
 */
@Component
@ConditionalOnProperty(
        prefix = "omagotchi.occupancy.telegram",
        name = "enabled",
        havingValue = "true"
)
public class TelegramVacancyAlertSender implements VacancyAlertSender {

    private static final int CONNECTION_REQUEST_TIMEOUT_MILLIS = 5_000;
    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;
    private static final int SOCKET_TIMEOUT_MILLIS = 10_000;

    /**
     * 저장은 UTC 그대로 두고, 사람이 읽는 문구에서만 KST로 바꾼다. {@code vacatedAt} 자체는
     * 이미 올바른 순간(instant)이라 저장을 바꿀 이유가 없다 — 텔레그램 문자를 읽는 사람이
     * 알아보기 쉽도록 표시만 바꾼다.
     *
     * <p>표기를 {@code zzz}로 <b>파생</b>시키는 것이 의도다. "(KST)"를 문자열로 박으면
     * {@link DateTimePolicy#ZONE_ID}가 바뀌었을 때 시각만 따라 바뀌고 라벨은 그대로 남아,
     * <b>틀린 시간대를 단언하는 문구</b>가 된다. Locale을 고정하는 것도 같은 이유로,
     * 서버 기본 Locale에 따라 표기가 흔들리지 않게 한다.</p>
     */
    private static final DateTimeFormatter DISPLAY_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss '('zzz')'", Locale.KOREA);

    private final AbsSender telegramSender;
    private final String chatId;

    @Autowired
    public TelegramVacancyAlertSender(
            @Value("${omagotchi.occupancy.telegram.bot-token}") String botToken,
            @Value("${omagotchi.occupancy.telegram.chat-id}") String chatId
    ) {
        this(new TelegramBotApiSender(requireConfigured(botToken, "TELEGRAM_BOT_TOKEN")), chatId);
    }

    TelegramVacancyAlertSender(AbsSender telegramSender, String chatId) {
        this.telegramSender = Objects.requireNonNull(telegramSender, "Telegram sender는 필수입니다.");
        this.chatId = requireConfigured(chatId, "TELEGRAM_CHAT_ID");
    }

    @Override
    public void sendVacancyAlert(VacancyNotice notice) {
        Objects.requireNonNull(notice, "공실 알림은 필수입니다.");
        send(messageOf(notice), "Telegram 공실 알림 발송에 실패했습니다.");
    }

    /**
     * 실제 전송. 두 통보가 같은 채팅으로 가고 성공 판정도 같아 여기 모은다.
     *
     * <p>동기 응답을 확인한 뒤에만 정상 반환한다 — 호출부가 그 반환을 <b>실제 발송 성공</b>
     * 으로 읽고 후속 처리를 하기 때문이다.</p>
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

    @Override
    public void sendDiscardNotice(DiscardNotice notice) {
        Objects.requireNonNull(notice, "삭제 통보는 필수입니다.");
        send(discardMessageOf(notice), "Telegram 공실 알림 삭제 통보 발송에 실패했습니다.");
    }

    /**
     * 삭제 통보 본문.
     *
     * <p><b>왜 사라졌는지를 반드시 적는다.</b> 이유가 없으면 사용자는 신청이 사라진 것만
     * 보고 다시 신청하려다 "이미 사용 가능한 회의실입니다"(400)를 받는다 — 비활성 공간에는
     * 활성 점유가 있을 수 없기 때문이다.</p>
     *
     * <p>재신청을 안내하지 않는 것도 의도다. 그 공간은 지금 신청 자체가 불가능하다.</p>
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

    /**
     * 알림 본문.
     *
     * <p>점유자·참여자를 담지 않는 것이 의도다 (MR-36). 회의실은 여러 기수가 공유하므로
     * "누가 쓰던 방인지"가 들어가면 타 기수 사용자의 개인정보가 신청자에게 노출된다.</p>
     *
     * <p>선착순임을 함께 알린다 (MR-04). 알림은 사용 권한을 보장하지 않는데, 그 사실을
     * 적지 않으면 알림을 받고 갔다가 이미 점유된 방을 보게 된다.</p>
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
