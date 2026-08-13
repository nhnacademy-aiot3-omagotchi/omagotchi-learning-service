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
import site.omagotchi.learningservice.occupancy.application.port.OccupancyReminderSender;

import java.util.Objects;

/**
 * 고정된 테스트용 Telegram 개인 채팅으로 점유 만료 임박 알림을 발송한다.
 *
 * <p>Telegram API의 동기 응답을 확인한 뒤에만 정상 반환한다. 오류를 예외로 전파해야
 * 호출부가 {@code reminder_sent_at}을 기록하지 않고 다음 스케줄에서 재시도할 수 있다.</p>
 *
 * <p>활성화 설정이 {@code true}일 때만 Bean이 등록된다. 비활성 상태에서는 no-op Bean을
 * 만들지 않아, 실제 sender가 없을 때 후보를 소진하지 않는 Application 정책을 유지한다.</p>
 */
@Component
@ConditionalOnProperty(
        prefix = "omagotchi.occupancy.telegram",
        name = "enabled",
        havingValue = "true"
)
public class TelegramOccupancyReminderSender implements OccupancyReminderSender {

    private static final int CONNECTION_REQUEST_TIMEOUT_MILLIS = 5_000;
    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;
    private static final int SOCKET_TIMEOUT_MILLIS = 10_000;

    private final AbsSender telegramSender;
    private final String chatId;

    @Autowired
    public TelegramOccupancyReminderSender(
            @Value("${omagotchi.occupancy.telegram.bot-token}") String botToken,
            @Value("${omagotchi.occupancy.telegram.chat-id}") String chatId
    ) {
        this(new TelegramBotApiSender(requireConfigured(botToken, "TELEGRAM_BOT_TOKEN")), chatId);
    }

    TelegramOccupancyReminderSender(AbsSender telegramSender, String chatId) {
        this.telegramSender = Objects.requireNonNull(telegramSender, "Telegram sender는 필수입니다.");
        this.chatId = requireConfigured(chatId, "TELEGRAM_CHAT_ID");
    }

    @Override
    public void sendExpiryReminder(ExpiryReminder reminder) {
        Objects.requireNonNull(reminder, "만료 임박 알림은 필수입니다.");

        SendMessage request = SendMessage.builder()
                .chatId(chatId)
                .text(messageOf(reminder))
                .build();

        Message response;
        try {
            response = telegramSender.execute(request);
        } catch (TelegramApiException exception) {
            throw new IllegalStateException("Telegram 만료 임박 알림 발송에 실패했습니다.", exception);
        }

        if (response == null || response.getMessageId() == null) {
            throw new IllegalStateException("Telegram 발송 성공 응답을 확인할 수 없습니다.");
        }
    }

    private static String messageOf(ExpiryReminder reminder) {
        return """
                [회의실 이용 종료 안내]

                공간 ID: %s
                이용 시간이 곧 종료됩니다.
                종료 예정 시각: %s
                """.formatted(reminder.spaceId(), reminder.expiresAt()).stripTrailing();
    }

    private static String requireConfigured(String value, String environmentVariable) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(environmentVariable + " 환경변수는 비어 있을 수 없습니다.");
        }
        return value;
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

    private static final class TelegramBotApiSender extends DefaultAbsSender {

        private TelegramBotApiSender(String botToken) {
            super(botOptions(), botToken);
        }
    }
}
