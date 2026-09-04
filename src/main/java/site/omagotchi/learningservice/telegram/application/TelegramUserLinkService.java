package site.omagotchi.learningservice.telegram.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.telegram.application.command.TelegramWebhookCommand;
import site.omagotchi.learningservice.telegram.application.command.UpdateTelegramNotificationCommand;
import site.omagotchi.learningservice.telegram.application.port.TelegramLinkTokenRepository;
import site.omagotchi.learningservice.telegram.application.port.TelegramUserLinkRepository;
import site.omagotchi.learningservice.telegram.application.result.TelegramLinkTokenResult;
import site.omagotchi.learningservice.telegram.application.result.TelegramUserLinkResult;
import site.omagotchi.learningservice.telegram.domain.TelegramErrorCode;
import site.omagotchi.learningservice.telegram.domain.TelegramLinkToken;
import site.omagotchi.learningservice.telegram.domain.TelegramUserLink;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TelegramUserLinkService {

    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final HexFormat HEX_FORMAT = HexFormat.of();

    private final TelegramUserLinkRepository userLinkRepository;
    private final TelegramLinkTokenRepository linkTokenRepository;
    private final TelegramProperties telegramProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 서비스 사용자가 Telegram Bot과 개인 대화를 시작할 수 있는 일회용 딥링크를 발급한다.
     */
    @Transactional
    public TelegramLinkTokenResult issueLinkToken(UUID userId) {
        String rawToken = generateRawToken();
        OffsetDateTime expiresAt = OffsetDateTime.now().plus(telegramProperties.linkToken().ttl());

        TelegramLinkToken token = TelegramLinkToken.issue(
                userId,
                TelegramTokenHash.sha256(rawToken),
                expiresAt
        );
        linkTokenRepository.save(token);

        return new TelegramLinkTokenResult(
                "https://t.me/" + telegramProperties.bot().username() + "?start=" + rawToken,
                expiresAt
        );
    }

    /**
     * Telegram Webhook의 /start token 메시지를 처리해 서비스 사용자와 개인 chat_id를 연결한다.
     */
    @Transactional
    public TelegramUserLinkResult linkByWebhook(TelegramWebhookCommand command) {
        WebhookStartCommand startCommand = parseStartCommand(command);
        TelegramLinkToken token = linkTokenRepository.findByTokenHash(TelegramTokenHash.sha256(startCommand.token()))
                .orElseThrow(() -> new BusinessException(TelegramErrorCode.TELEGRAM_LINK_TOKEN_NOT_FOUND));

        OffsetDateTime now = OffsetDateTime.now();
        if (!token.isUsableAt(now)) {
            throw new BusinessException(TelegramErrorCode.TELEGRAM_LINK_TOKEN_EXPIRED);
        }

        validateTelegramChatOwnership(token.getUserId(), startCommand.telegramChatId(), startCommand.telegramUserId());

        Optional<TelegramUserLink> active = userLinkRepository.findActiveByUserId(token.getUserId());
        if (active.isPresent()) {
            token.markUsed(now);
            return TelegramUserLinkResult.from(resolveRelink(active.get(), startCommand));
        }

        // 해제된 행을 되살리지 않고 새 행을 남긴다. 부분 유니크(V22)가 활성 행만 보므로
        // 재연동이 막히지 않고, 연동·해제 이력이 순서대로 쌓인다.
        TelegramUserLink link = TelegramUserLink.link(
                token.getUserId(),
                startCommand.telegramUserId(),
                startCommand.telegramChatId()
        );

        token.markUsed(now);
        return TelegramUserLinkResult.from(userLinkRepository.save(link));
    }

    public Optional<TelegramUserLinkResult> findByChatId(Long telegramChatId) {
        if (Objects.isNull(telegramChatId)) {
            return Optional.empty();
        }

        return userLinkRepository.findActiveByTelegramChatId(telegramChatId)
                .map(TelegramUserLinkResult::from);
    }

    public TelegramUserLinkResult getMyLink(UUID userId) {
        return TelegramUserLinkResult.from(requireActiveLink(userId));
    }

    @Transactional
    public TelegramUserLinkResult updateNotification(UUID userId, UpdateTelegramNotificationCommand command) {
        TelegramUserLink link = requireActiveLink(userId);

        link.changeNotificationEnabled(command.enabled());
        return TelegramUserLinkResult.from(link);
    }

    @Transactional
    public TelegramUserLinkResult disconnect(UUID userId) {
        TelegramUserLink link = requireActiveLink(userId);
        link.disconnect();

        return TelegramUserLinkResult.from(link);
    }

    /**
     * 이미 활성 연동이 있는 사용자의 {@code /start} 를 저장 전에 가른다.
     *
     * <p>같은 텔레그램 계정이면 새 행을 만들지 않고 기존 연동을 그대로 돌려준다 — 딥링크를
     * 두 번 눌렀거나 텔레그램이 같은 Update 를 재전송한 경우라 결과가 같아야 한다.</p>
     *
     * <p>다른 텔레그램 계정이면 업무 오류로 거절한다. 여기서 조용히 기존 연동을 돌려주면
     * 봇이 "연동이 완료되었습니다"라고 답하는데 정작 그 대화로는 알림이 가지 않는다.
     * 자동으로 해제하고 갈아끼우지도 않는다 — 사용자가 요청한 적 없는 해제다.</p>
     *
     * <p>이 검사가 없으면 {@code uq_telegram_user_links_active_user} 위반으로 떨어지고,
     * 웹훅은 재시도해도 낫지 않을 실패를 "일시적인 오류"로 안내하게 된다.</p>
     */
    private TelegramUserLink resolveRelink(TelegramUserLink active, WebhookStartCommand startCommand) {
        boolean sameTelegramAccount =
                Objects.equals(active.getTelegramChatId(), startCommand.telegramChatId())
                        && Objects.equals(active.getTelegramUserId(), startCommand.telegramUserId());

        if (!sameTelegramAccount) {
            throw new BusinessException(TelegramErrorCode.TELEGRAM_USER_ALREADY_LINKED);
        }
        return active;
    }

    /**
     * 조회가 연동 중인 행만 보므로, 앞서 해제한 사용자의 행은 이 검증을 막지 않는다.
     * 해제는 곧 그 텔레그램 계정을 놓아준다는 뜻이다.
     */
    private void validateTelegramChatOwnership(UUID userId, Long telegramChatId, Long telegramUserId) {
        userLinkRepository.findActiveByTelegramChatId(telegramChatId)
                .filter(link -> !link.getUserId().equals(userId))
                .ifPresent(link -> {
                    throw new BusinessException(TelegramErrorCode.TELEGRAM_CHAT_ALREADY_LINKED);
                });

        userLinkRepository.findActiveByTelegramUserId(telegramUserId)
                .filter(link -> !link.getUserId().equals(userId))
                .ifPresent(link -> {
                    throw new BusinessException(TelegramErrorCode.TELEGRAM_CHAT_ALREADY_LINKED);
                });
    }

    private WebhookStartCommand parseStartCommand(TelegramWebhookCommand command) {
        if (command == null
                || command.telegramChatId() == null
                || command.telegramUserId() == null
                || command.text() == null) {
            throw new BusinessException(TelegramErrorCode.TELEGRAM_WEBHOOK_UNSUPPORTED);
        }

        String text = command.text().trim();
        if (!text.startsWith("/start ")) {
            throw new BusinessException(TelegramErrorCode.TELEGRAM_WEBHOOK_UNSUPPORTED);
        }

        String token = text.substring("/start ".length()).trim();
        if (token.isBlank()) {
            throw new BusinessException(TelegramErrorCode.TELEGRAM_WEBHOOK_UNSUPPORTED);
        }

        return new WebhookStartCommand(
                token,
                command.telegramUserId(),
                command.telegramChatId()
        );
    }
    private TelegramUserLink requireActiveLink(UUID userId){
        return userLinkRepository.findActiveByUserId(userId)
                .orElseThrow(() -> new BusinessException(TelegramErrorCode.TELEGRAM_USER_LINK_NOT_FOUND));
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(bytes);
        return HEX_FORMAT.formatHex(bytes);
    }

    private record WebhookStartCommand(
            String token,
            Long telegramUserId,
            Long telegramChatId
    ) {
    }
}
