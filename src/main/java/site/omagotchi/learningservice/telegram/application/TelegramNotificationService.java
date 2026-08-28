package site.omagotchi.learningservice.telegram.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import site.omagotchi.learningservice.telegram.application.port.TelegramMessageSender;
import site.omagotchi.learningservice.telegram.application.port.TelegramUserLinkRepository;
import site.omagotchi.learningservice.telegram.domain.TelegramUserLink;

import java.time.Duration;
import java.util.Objects;
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
@Service
@RequiredArgsConstructor
public class TelegramNotificationService {

    private final TelegramUserLinkRepository userLinkRepository;
    private final TelegramMessageSender messageSender;

    /**
     * 한 사람에게 보낸다.
     *
     * <p><b>트랜잭션을 걸지 않는다.</b> 걸면 DB 커넥션을 쥔 채 api.telegram.org 응답을
     * 기다리게 되는데, read 타임아웃이 10초라 그동안 커넥션이 묶인다. 조치 알림은 MQ
     * 리스너 3~5개가 동시에 도는 경로라 기본 풀(10)이 금방 마른다.</p>
     *
     * <p>조회 한 번은 Spring Data가 자체 트랜잭션으로 처리한다. {@link TelegramUserLink}는
     * 연관관계가 없어 준영속 상태에서도 값을 그대로 읽을 수 있다.</p>
     *
     * @return 실제로 발송을 시도해 성공했으면 {@code true}, 미연동이거나 알림을 받지 않는
     *         사용자라 시도조차 하지 않았으면 {@code false}
     * @throws IllegalStateException 발송을 시도했으나 실패한 경우
     */
    public boolean send(UUID recipientUserId, String text) {
        return send(recipientUserId, text, null);
    }

    /**
     * 제한 시간 안에 보낸다. {@code timeout}이 {@code null}이면 설정된 read 타임아웃을 쓴다.
     *
     * <p>호출 스레드를 오래 붙잡으면 안 되는 경로(조치 알림)가 소비처다.</p>
     */
    public boolean send(UUID recipientUserId, String text, Duration timeout) {
        Optional<TelegramUserLink> link = userLinkRepository.findByUserId(recipientUserId);

        if (link.isEmpty()) {
            log.debug("텔레그램 미연동 사용자라 발송하지 않습니다. recipientUserId={}", recipientUserId);
            return false;
        }
        if (!link.get().canReceiveNotification()) {
            log.debug("텔레그램 알림을 받지 않는 사용자라 발송하지 않습니다. recipientUserId={}", recipientUserId);
            return false;
        }

        Long chatId = link.get().getTelegramChatId();

        if (Objects.isNull(timeout)) {
            messageSender.send(chatId, text);
        } else {
            messageSender.send(chatId, text, timeout);
        }
        return true;
    }
}
