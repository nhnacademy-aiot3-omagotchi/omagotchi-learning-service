package site.omagotchi.learningservice.telegram.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.telegram.application.command.TelegramWebhookCommand;
import site.omagotchi.learningservice.telegram.application.port.TelegramLinkTokenRepository;
import site.omagotchi.learningservice.telegram.application.port.TelegramUserLinkRepository;
import site.omagotchi.learningservice.telegram.application.result.TelegramUserLinkResult;
import site.omagotchi.learningservice.telegram.domain.TelegramErrorCode;
import site.omagotchi.learningservice.telegram.domain.TelegramLinkToken;
import site.omagotchi.learningservice.telegram.domain.TelegramUserLink;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 이미 활성 연동이 있는 사용자의 {@code /start} 처리를 고정한다.
 *
 * <p>이 분기가 없으면 새 행을 만들다 {@code uq_telegram_user_links_active_user} 를 위반하고,
 * 웹훅은 재시도해도 낫지 않을 실패를 "일시적인 오류"로 안내한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class TelegramUserLinkServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final Long CHAT_ID = 987654L;
    private static final Long TELEGRAM_USER_ID = 111L;
    private static final String RAW_TOKEN = "abcdef";

    @Mock
    private TelegramUserLinkRepository userLinkRepository;

    @Mock
    private TelegramLinkTokenRepository linkTokenRepository;

    private TelegramUserLinkService service;

    private TelegramUserLinkService service() {
        if (service == null) {
            service = new TelegramUserLinkService(
                    userLinkRepository,
                    linkTokenRepository,
                    properties()
            );
        }
        return service;
    }

    @Test
    @DisplayName("활성 연동이 없으면 새 연동 행을 저장한다")
    void savesNewLinkWhenNotLinked() {
        givenUsableToken();
        givenNoOwnershipConflict();
        given(userLinkRepository.findActiveByUserId(USER_ID)).willReturn(Optional.empty());
        given(userLinkRepository.save(any(TelegramUserLink.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        TelegramUserLinkResult result = service().linkByWebhook(startCommand(TELEGRAM_USER_ID, CHAT_ID));

        assertThat(result.telegramChatId()).isEqualTo(CHAT_ID);
        verify(userLinkRepository).save(any(TelegramUserLink.class));
    }

    @Test
    @DisplayName("같은 텔레그램 계정의 재연동은 새 행을 만들지 않고 기존 연동을 돌려준다")
    void returnsExistingLinkOnSameTelegramAccount() {
        TelegramLinkToken token = givenUsableToken();
        givenNoOwnershipConflict();
        TelegramUserLink active = TelegramUserLink.link(USER_ID, TELEGRAM_USER_ID, CHAT_ID);
        given(userLinkRepository.findActiveByUserId(USER_ID)).willReturn(Optional.of(active));

        TelegramUserLinkResult result = service().linkByWebhook(startCommand(TELEGRAM_USER_ID, CHAT_ID));

        assertThat(result.telegramChatId()).isEqualTo(CHAT_ID);
        assertThat(result.linkedAt()).isEqualTo(active.getLinkedAt());
        // 새 행을 만들지 않는 것이 이 분기의 핵심이다.
        verify(userLinkRepository, never()).save(any(TelegramUserLink.class));
        // 딥링크는 소모됐다. 다시 눌러도 같은 토큰이 통하면 안 된다.
        assertThat(token.isUsableAt(OffsetDateTime.now())).isFalse();
    }

    @Test
    @DisplayName("다른 텔레그램 계정으로 재연동하면 저장 전에 업무 오류로 거절한다")
    void rejectsRelinkFromDifferentTelegramAccount() {
        givenUsableToken();
        Long otherChatId = CHAT_ID + 1;
        Long otherTelegramUserId = TELEGRAM_USER_ID + 1;
        given(userLinkRepository.findActiveByTelegramChatId(otherChatId)).willReturn(Optional.empty());
        given(userLinkRepository.findActiveByTelegramUserId(otherTelegramUserId)).willReturn(Optional.empty());
        given(userLinkRepository.findActiveByUserId(USER_ID))
                .willReturn(Optional.of(TelegramUserLink.link(USER_ID, TELEGRAM_USER_ID, CHAT_ID)));

        assertThatThrownBy(() -> service().linkByWebhook(startCommand(otherTelegramUserId, otherChatId)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isSameAs(TelegramErrorCode.TELEGRAM_USER_ALREADY_LINKED));

        verify(userLinkRepository, never()).save(any(TelegramUserLink.class));
    }

    private TelegramLinkToken givenUsableToken() {
        TelegramLinkToken token = TelegramLinkToken.issue(
                USER_ID,
                TelegramTokenHash.sha256(RAW_TOKEN),
                OffsetDateTime.now().plusMinutes(10)
        );
        given(linkTokenRepository.findByTokenHash(anyString())).willReturn(Optional.of(token));
        return token;
    }

    private void givenNoOwnershipConflict() {
        given(userLinkRepository.findActiveByTelegramChatId(CHAT_ID)).willReturn(Optional.empty());
        given(userLinkRepository.findActiveByTelegramUserId(TELEGRAM_USER_ID)).willReturn(Optional.empty());
    }

    private static TelegramWebhookCommand startCommand(Long telegramUserId, Long chatId) {
        return new TelegramWebhookCommand(telegramUserId, chatId, "/start " + RAW_TOKEN);
    }

    /** {@code linkByWebhook} 은 설정을 쓰지 않는다. 생성자를 채우기 위한 최소 값이다. */
    private static TelegramProperties properties() {
        return new TelegramProperties(
                new TelegramProperties.Bot("omagotchi_bot", "token", 4, null),
                new TelegramProperties.LinkToken(Duration.ofMinutes(10)),
                new TelegramProperties.Webhook("secret")
        );
    }
}
