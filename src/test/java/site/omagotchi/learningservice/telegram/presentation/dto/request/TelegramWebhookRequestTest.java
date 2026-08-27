package site.omagotchi.learningservice.telegram.presentation.dto.request;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import site.omagotchi.learningservice.telegram.application.dto.command.TelegramWebhookCommand;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 어떤 Update를 안으로 들여보낼지 고르는 규칙을 고정한다.
 *
 * <p>웹훅은 봇당 하나라 <b>봇에게 오는 모든 것</b>이 여기로 온다. 걸러낸 것은 식별자가
 * 모두 {@code null}인 빈 Command가 되고, Service가 그것을 조용히 넘긴다.</p>
 */
class TelegramWebhookRequestTest {

    private static final Long CHAT_ID = 42L;
    private static final Long FROM_ID = 7L;

    @Test
    @DisplayName("개인 대화 메시지는 그대로 통과한다.")
    void passesPrivateChatMessage() {
        TelegramWebhookCommand command = request("private", "/status").toCommand();

        assertThat(command.telegramChatId()).isEqualTo(CHAT_ID);
        assertThat(command.telegramUserId()).isEqualTo(FROM_ID);
        assertThat(command.text()).isEqualTo("/status");
    }

    /**
     * <b>그룹에서 {@code /start <token>}을 치면 그 그룹의 chat_id가 연동된다.</b> 그러면
     * 개인 알림이 그룹 전체에 뿌려진다. 딥링크는 개인 대화로 유도하지만 손으로 치는
     * 것까지 막지는 못하므로 여기서 막는다.
     *
     * <p>회신조차 하지 않는 것도 의도다 — 봇을 아무 그룹에나 넣고 명령을 쳐서 우리
     * 이름으로 메시지를 뿌리게 할 수 있다.</p>
     */
    @ParameterizedTest(name = "chat.type={0}")
    @DisplayName("개인 대화가 아니면 버린다.")
    @NullSource
    @ValueSource(strings = {"group", "supergroup", "channel"})
    void dropsNonPrivateChat(String chatType) {
        assertThat(request(chatType, "/start token").toCommand()).isEqualTo(empty());
    }

    @Test
    @DisplayName("메시지가 없는 Update는 버린다.")
    void dropsUpdateWithoutMessage() {
        assertThat(new TelegramWebhookRequest(1L, null).toCommand()).isEqualTo(empty());
    }

    @Test
    @DisplayName("보낸 사람이 없는 Update는 버린다.")
    void dropsUpdateWithoutSender() {
        TelegramWebhookRequest request = new TelegramWebhookRequest(1L,
                new TelegramWebhookRequest.Message(
                        new TelegramWebhookRequest.Chat(CHAT_ID, "private"), null, "/status"));

        assertThat(request.toCommand()).isEqualTo(empty());
    }

    private static TelegramWebhookRequest request(String chatType, String text) {
        return new TelegramWebhookRequest(1L, new TelegramWebhookRequest.Message(
                new TelegramWebhookRequest.Chat(CHAT_ID, chatType),
                new TelegramWebhookRequest.From(FROM_ID, false, "테스터", "tester"),
                text));
    }

    private static TelegramWebhookCommand empty() {
        return new TelegramWebhookCommand(null, null, null);
    }
}
