package site.omagotchi.learningservice.telegram.presentation.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import site.omagotchi.learningservice.telegram.application.command.TelegramWebhookCommand;

/**
 * Telegram Bot Webhook 요청
 */
public record TelegramWebhookRequest(
        @JsonProperty("update_id") Long updateId,
        Message message
) {

    /** {@code chat.type}. 개인 대화만 받는다 */
    private static final String PRIVATE_CHAT = "private";

    /**
     * 우리가 다루는 Update만 골라 Command로 바꾼다. 아니면 식별자가 모두 {@code null}인
     * 빈 Command를 돌려주고, 그것을 Service가 조용히 넘긴다.
     *
     * <p><b>개인 대화가 아니면 버린다.</b> 그룹에서 {@code /start <token>}을 치면 그 그룹의
     * chat_id가 연동되어 <b>개인 알림이 그룹 전체에 뿌려진다.</b> 딥링크는 개인 대화로
     * 유도하지만 손으로 치는 것까지 막지는 못한다.</p>
     *
     * <p>그룹에 회신하지 않는 것도 의도다 — 봇을 아무 그룹에나 넣고 명령을 쳐서 우리
     * 이름으로 메시지를 뿌리게 할 수 있다.</p>
     */
    public TelegramWebhookCommand toCommand() {
        if (message == null || message.chat() == null || message.from() == null) {
            return new TelegramWebhookCommand(null, null, null);
        }
        if (!PRIVATE_CHAT.equals(message.chat().type())) {
            return new TelegramWebhookCommand(null, null, null);
        }
        return new TelegramWebhookCommand(message.from().id(), message.chat().id(), message.text());
    }

    public record Message(
            Chat chat,
            From from,
            String text
    ) {
    }

    public record Chat(
            Long id,
            String type
    ) {
    }

    public record From(
            Long id,
            @JsonProperty("is_bot") Boolean bot,
            @JsonProperty("first_name") String firstName,
            String username
    ) {
    }
}
