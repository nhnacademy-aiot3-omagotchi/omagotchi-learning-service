package site.omagotchi.learningservice.telegram.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.telegram.domain.TelegramUserLink;
import site.omagotchi.learningservice.telegram.infrastructure.TelegramMessageSender;
import site.omagotchi.learningservice.telegram.infrastructure.TelegramUserLinkRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 수신자 해석 규칙을 고정한다.
 *
 * <p><b>"발송 대상 아님"과 "발송 실패"를 구분하는 것이 이 Class의 핵심이다.</b> 미연동·알림
 * 끔·연동 해제는 사용자가 의도한 정상 상태라 예외를 던지지 않는다 — 던지면 에러 로그만
 * 쌓이고 재시도해도 결과가 같다. 반대로 실제 발송 실패는 전파해야 호출부가 재시도할 수
 * 있다.</p>
 */
@ExtendWith(MockitoExtension.class)
class TelegramNotificationServiceTest {

    private static final UUID RECIPIENT = UUID.randomUUID();
    private static final Long CHAT_ID = 12345L;
    private static final String TEXT = "본문";

    @Mock
    private TelegramUserLinkRepository userLinkRepository;

    @Mock
    private TelegramMessageSender messageSender;

    @InjectMocks
    private TelegramNotificationService service;

    @Test
    @DisplayName("연동된 사용자에게는 그 사람의 개인 채팅으로 보낸다.")
    void sendsToLinkedUsersOwnChat() {
        given(userLinkRepository.findByUserId(RECIPIENT)).willReturn(Optional.of(linkedUser()));

        service.send(RECIPIENT, TEXT);

        verify(messageSender).send(CHAT_ID, TEXT);
    }

    /** 미연동은 오류가 아니다 — 아직 연동하지 않았을 뿐이다. */
    @Test
    @DisplayName("미연동 사용자에게는 발송하지 않고 조용히 끝낸다.")
    void skipsUnlinkedUser() {
        given(userLinkRepository.findByUserId(RECIPIENT)).willReturn(Optional.empty());

        assertThatCode(() -> service.send(RECIPIENT, TEXT)).doesNotThrowAnyException();

        verify(messageSender, never()).send(anyLong(), anyString());
    }

    /** 사용자가 직접 끈 것이므로 존중한다. */
    @Test
    @DisplayName("알림을 끈 사용자에게는 발송하지 않는다.")
    void skipsUserWithNotificationDisabled() {
        TelegramUserLink link = linkedUser();
        link.changeNotificationEnabled(false);
        given(userLinkRepository.findByUserId(RECIPIENT)).willReturn(Optional.of(link));

        assertThatCode(() -> service.send(RECIPIENT, TEXT)).doesNotThrowAnyException();

        verify(messageSender, never()).send(anyLong(), anyString());
    }

    /**
     * 연동 해제는 알림 끔과 다른 상태다 — 연결 자체가 끊긴 것이라 다시 연동해야 한다.
     * 한 판정에 묶여 있으면 둘 중 하나만 확인하는 실수가 생긴다.
     */
    @Test
    @DisplayName("연동을 해제한 사용자에게는 발송하지 않는다.")
    void skipsDisconnectedUser() {
        TelegramUserLink link = linkedUser();
        link.disconnect();
        given(userLinkRepository.findByUserId(RECIPIENT)).willReturn(Optional.of(link));

        assertThatCode(() -> service.send(RECIPIENT, TEXT)).doesNotThrowAnyException();

        verify(messageSender, never()).send(anyLong(), anyString());
    }

    /**
     * 실제 발송 실패는 "대상 아님"과 달리 전파해야 한다. 삼키면 호출부가 소진 기록을 남겨
     * <b>보내지 못한 알림이 보낸 것으로 기록된다.</b>
     */
    @Test
    @DisplayName("발송 실패는 호출부로 전파한다.")
    void propagatesSendFailure() {
        given(userLinkRepository.findByUserId(RECIPIENT)).willReturn(Optional.of(linkedUser()));
        willThrow(new IllegalStateException("발송 실패"))
                .given(messageSender).send(anyLong(), anyString());

        assertThatThrownBy(() -> service.send(RECIPIENT, TEXT))
                .isInstanceOf(IllegalStateException.class);
    }

    private static TelegramUserLink linkedUser() {
        return TelegramUserLink.link(RECIPIENT, 999L, CHAT_ID);
    }
}
