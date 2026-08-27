package site.omagotchi.learningservice.telegram.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.telegram.domain.TelegramUserLink;
import site.omagotchi.learningservice.telegram.infrastructure.TelegramMessageSender;
import site.omagotchi.learningservice.telegram.infrastructure.TelegramUserLinkRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * 다른 Feature가 텔레그램으로 알림을 보내는 유일한 진입점.
 *
 * <p><b>계정별로 보낸다.</b> {@code telegram_user_links}에서 {@code userId → chat_id}를 찾아
 * 그 사람의 개인 채팅으로 발송한다 — 연동·알림 설정을 사용자가 이미 관리하고 있으므로 그
 * 상태를 그대로 존중한다.</p>
 */
@Slf4j
@RequiredArgsConstructor
public class TelegramNotificationService {

    private final TelegramUserLinkRepository userLinkRepository;
    private final TelegramMessageSender messageSender;

    /**
     * 한 사람에게 보낸다.
     *
     * @return 실제로 발송을 시도해 성공했으면 {@code true}, 미연동이거나 알림을 받지 않는
     *         사용자라 시도조차 하지 않았으면 {@code false}
     * @throws IllegalStateException 발송을 시도했으나 실패한 경우
     */
    @Transactional(readOnly = true)
    public boolean send(UUID recipientUserId, String text) {
        Optional<TelegramUserLink> link = userLinkRepository.findByUserId(recipientUserId);

        if (link.isEmpty()) {
            log.debug("텔레그램 미연동 사용자라 발송하지 않습니다. recipientUserId={}", recipientUserId);
            return false;
        }
        if (!link.get().canReceiveNotification()) {
            log.debug("텔레그램 알림을 받지 않는 사용자라 발송하지 않습니다. recipientUserId={}", recipientUserId);
            return false;
        }

        messageSender.send(link.get().getTelegramChatId(), text);
        return true;
    }
}
