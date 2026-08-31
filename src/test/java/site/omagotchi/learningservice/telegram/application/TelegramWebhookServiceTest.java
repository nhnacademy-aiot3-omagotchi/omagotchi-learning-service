package site.omagotchi.learningservice.telegram.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.telegram.application.command.TelegramWebhookCommand;
import site.omagotchi.learningservice.telegram.application.command.UpdateTelegramNotificationCommand;
import site.omagotchi.learningservice.telegram.application.result.TelegramUserLinkResult;
import site.omagotchi.learningservice.telegram.domain.TelegramErrorCode;
import site.omagotchi.learningservice.telegram.application.port.TelegramMessageSender;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 봇 명령 처리 규칙을 고정한다.
 *
 * <p><b>예외가 밖으로 나가지 않는 것이 이 Class의 핵심 계약이다.</b> 웹훅은 봇당 하나라
 * 그 봇에게 오는 모든 메시지가 여기로 들어오고, 2xx가 아니면 텔레그램이 재시도한다 —
 * 인사말 하나가 무한 재시도를 만들면 안 된다.</p>
 *
 * <p>상태 변경과 회신을 나눠 보는 것도 요점이다. 회신이 실패해도 연동·해제는 이미
 * 끝난 일이라 되돌리지 않는다.</p>
 */
@ExtendWith(MockitoExtension.class)
class TelegramWebhookServiceTest {

    private static final Long CHAT_ID = 987654L;
    private static final Long TELEGRAM_USER_ID = 111L;
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private TelegramUserLinkService userLinkService;

    @Mock
    private CohortMembershipQueryService membershipQueryService;

    @Mock
    private TelegramMessageSender messageSender;

    @InjectMocks
    private TelegramWebhookService service;

    @Captor
    private ArgumentCaptor<String> replyCaptor;

    // ────────────────────────────── 연동 ──────────────────────────────

    @Test
    @DisplayName("/start는 토큰을 그대로 연동에 넘기고 완료를 알린다.")
    void linksOnStart() {
        service.handle(command("/start a1b2c3"));

        verify(userLinkService).linkByWebhook(any(TelegramWebhookCommand.class));
        assertThat(reply()).contains("연동이 완료되었습니다");
    }

    // ────────────────────────────── 알림 끄기·켜기 ──────────────────────────────

    @Test
    @DisplayName("/stop은 알림만 끄고 연동은 남긴다.")
    void turnsNotificationOffOnStop() {
        givenLinked(true);

        service.handle(command("/stop"));

        verify(userLinkService).updateNotification(USER_ID, new UpdateTelegramNotificationCommand(false));
        verify(userLinkService, never()).disconnect(any());
        assertThat(reply()).contains("알림을 껐습니다", "/resume");
    }

    @Test
    @DisplayName("/resume은 알림을 다시 켠다.")
    void turnsNotificationOnOnResume() {
        givenLinked(false);

        service.handle(command("/resume"));

        verify(userLinkService).updateNotification(USER_ID, new UpdateTelegramNotificationCommand(true));
    }

    /**
     * 되돌리는 비용을 <b>사후에</b> 알린다. 확인 절차를 두지 않는 대신 복구 경로를 보여 준다.
     */
    @Test
    @DisplayName("/disconnect는 연동을 끊고 복구 방법을 함께 알린다.")
    void disconnectsOnDisconnect() {
        givenLinked(true);

        service.handle(command("/disconnect"));

        verify(userLinkService).disconnect(USER_ID);
        assertThat(reply()).contains("연동을 해제했습니다", "/stop");
    }

    // ────────────────────────────── 상태 조회 ──────────────────────────────

    @Test
    @DisplayName("/status는 매니저에게만 조치 알림을 목록에 넣는다.")
    void showsActionAlertOnlyToManagers() {
        givenLinked(true);
        given(membershipQueryService.isActiveManager(USER_ID)).willReturn(true);

        service.handle(command("/status"));

        assertThat(reply()).contains("알림: 켜짐", "자동 조치 결과");
    }

    @Test
    @DisplayName("/status는 매니저가 아니면 조치 알림을 감춘다.")
    void hidesActionAlertFromNonManagers() {
        givenLinked(false);
        given(membershipQueryService.isActiveManager(USER_ID)).willReturn(false);

        service.handle(command("/status"));

        assertThat(reply()).contains("알림: 꺼짐").doesNotContain("자동 조치 결과");
    }

    // ────────────────────────────── 연동이 없을 때 ──────────────────────────────

    /**
     * 연동이 없으면 <b>상태를 건드리지 않는다.</b> chat_id로 사람을 특정할 수 없으므로
     * 무엇을 끄고 켤지 알 수 없다.
     */
    @Test
    @DisplayName("연동이 없으면 상태를 바꾸지 않고 연동 방법만 알린다.")
    void guidesWhenNotLinked() {
        given(userLinkService.findByChatId(CHAT_ID)).willReturn(Optional.empty());

        service.handle(command("/stop"));

        verify(userLinkService, never()).updateNotification(any(), any());
        assertThat(reply()).contains("연동된 계정이 없습니다");
    }

    // ────────────────────────────── 명령이 아닌 것 ──────────────────────────────

    @Test
    @DisplayName("모르는 입력에는 도움말로 답한다.")
    void answersUnknownInputWithHelp() {
        service.handle(command("안녕하세요"));

        assertThat(reply()).contains("오마고치 알림 봇입니다");
        verifyNoInteractions(userLinkService);
    }

    /**
     * chat_id가 없는 Update는 우리가 다루는 종류가 아니다(개인 대화가 아니거나 메시지가
     * 아니다). <b>회신도 하지 않는다</b> — 보낼 곳을 모른다.
     */
    @Test
    @DisplayName("chat_id가 없으면 아무것도 하지 않는다.")
    void ignoresUpdateWithoutChatId() {
        service.handle(new TelegramWebhookCommand(TELEGRAM_USER_ID, null, "/status"));

        verifyNoInteractions(userLinkService, membershipQueryService, messageSender);
    }

    // ────────────────────────────── 실패 ──────────────────────────────

    @Test
    @DisplayName("만료된 토큰은 예외 대신 다시 받으라는 안내가 된다.")
    void explainsExpiredToken() {
        willThrow(new BusinessException(TelegramErrorCode.TELEGRAM_LINK_TOKEN_EXPIRED))
                .given(userLinkService).linkByWebhook(any());

        assertThatCode(() -> service.handle(command("/start expired"))).doesNotThrowAnyException();
        assertThat(reply()).contains("만료되었거나 이미 사용");
    }

    @Test
    @DisplayName("이미 다른 계정이 쓰는 텔레그램이면 그 사실을 알린다.")
    void explainsAlreadyLinkedChat() {
        willThrow(new BusinessException(TelegramErrorCode.TELEGRAM_CHAT_ALREADY_LINKED))
                .given(userLinkService).linkByWebhook(any());

        service.handle(command("/start taken"));

        assertThat(reply()).contains("이미 다른 사용자와 연결");
    }

    /**
     * 예상하지 못한 실패도 밖으로 내보내지 않는다. 텔레그램에게 5xx를 주면 같은 Update를
     * 계속 다시 보내는데, 재시도해도 결과가 같다.
     */
    @Test
    @DisplayName("예상 못 한 실패도 밖으로 내보내지 않는다.")
    void swallowsUnexpectedFailure() {
        given(userLinkService.findByChatId(CHAT_ID)).willThrow(new IllegalStateException("DB 장애"));

        assertThatCode(() -> service.handle(command("/status"))).doesNotThrowAnyException();
        assertThat(reply()).contains("일시적인 오류");
    }

    /**
     * 회신 실패가 이미 끝난 상태 변경을 되돌리지 않는다. 여기서 예외가 나가면 텔레그램이
     * 같은 {@code /disconnect}를 다시 보내고, 그때는 연동이 없어 엉뚱한 안내가 나간다.
     */
    @Test
    @DisplayName("회신에 실패해도 상태 변경은 그대로 두고 조용히 끝낸다.")
    void keepsStateChangeWhenReplyFails() {
        givenLinked(true);
        willThrow(new IllegalStateException("발송 실패")).given(messageSender).send(anyLong(), anyString());

        assertThatCode(() -> service.handle(command("/disconnect"))).doesNotThrowAnyException();
        verify(userLinkService).disconnect(USER_ID);
    }

    // ────────────────────────────── 헬퍼 ──────────────────────────────

    private static TelegramWebhookCommand command(String text) {
        return new TelegramWebhookCommand(TELEGRAM_USER_ID, CHAT_ID, text);
    }

    private void givenLinked(boolean notificationEnabled) {
        given(userLinkService.findByChatId(CHAT_ID)).willReturn(Optional.of(new TelegramUserLinkResult(
                USER_ID, TELEGRAM_USER_ID, CHAT_ID, notificationEnabled,
                OffsetDateTime.parse("2026-08-01T00:00:00Z"), null)));
    }

    /** 사용자에게 실제로 나간 문구. */
    private String reply() {
        verify(messageSender).send(eq(CHAT_ID), replyCaptor.capture());
        return replyCaptor.getValue();
    }
}
