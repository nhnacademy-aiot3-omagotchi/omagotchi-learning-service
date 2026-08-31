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

        TelegramUserLink link = userLinkRepository.findByUserId(token.getUserId())
                .map(existing -> {
                    existing.reconnect(startCommand.telegramUserId(), startCommand.telegramChatId());
                    return existing;
                })
                .orElseGet(() -> TelegramUserLink.link(
                        token.getUserId(),
                        startCommand.telegramUserId(),
                        startCommand.telegramChatId()
                ));

        token.markUsed(now);
        return TelegramUserLinkResult.from(userLinkRepository.save(link));
    }

    public Optional<TelegramUserLinkResult> findByChatId(Long telegramChatId) {
        if (Objects.isNull(telegramChatId)) {
            return Optional.empty();
        }

        return userLinkRepository.findByTelegramChatId(telegramChatId)
                .filter(link -> Objects.isNull(link.getDisconnectedAt()))
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

    private void validateTelegramChatOwnership(UUID userId, Long telegramChatId, Long telegramUserId) {
        userLinkRepository.findByTelegramChatId(telegramChatId)
                .filter(link -> !link.getUserId().equals(userId))
                .ifPresent(link -> {
                    throw new BusinessException(TelegramErrorCode.TELEGRAM_CHAT_ALREADY_LINKED);
                });

        userLinkRepository.findByTelegramUserId(telegramUserId)
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
        return userLinkRepository.findByUserId(userId)
                .filter(link -> Objects.isNull(link.getDisconnectedAt()))
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
