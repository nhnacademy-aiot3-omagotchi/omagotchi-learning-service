package site.omagotchi.learningservice.telegram.application;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.telegram.application.dto.command.TelegramWebhookCommand;
import site.omagotchi.learningservice.telegram.application.dto.command.UpdateTelegramNotificationCommand;
import site.omagotchi.learningservice.telegram.application.dto.result.TelegramLinkTokenResponse;
import site.omagotchi.learningservice.telegram.application.dto.result.TelegramUserLinkResponse;
import site.omagotchi.learningservice.telegram.domain.TelegramErrorCode;
import site.omagotchi.learningservice.telegram.domain.TelegramLinkToken;
import site.omagotchi.learningservice.telegram.domain.TelegramUserLink;
import site.omagotchi.learningservice.telegram.infrastructure.TelegramLinkTokenRepository;
import site.omagotchi.learningservice.telegram.infrastructure.TelegramUserLinkRepository;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TelegramUserLinkService {

    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final HexFormat HEX_FORMAT = HexFormat.of();

    private final TelegramUserLinkRepository userLinkRepository;
    private final TelegramLinkTokenRepository linkTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${telegram.bot.username:omagotchi_bot}")
    private String botUsername;

    @Value("${telegram.link-token.ttl:PT10M}")
    private Duration linkTokenTtl;

    /**
     * 서비스 사용자가 Telegram Bot과 개인 대화를 시작할 수 있는 일회용 딥링크를 발급한다.
     */
    @Transactional
    public TelegramLinkTokenResponse issueLinkToken(Long userId) {
        String rawToken = generateRawToken();
        OffsetDateTime expiresAt = OffsetDateTime.now().plus(linkTokenTtl);

        TelegramLinkToken token = TelegramLinkToken.issue(
                userId,
                TelegramTokenHash.sha256(rawToken),
                expiresAt
        );
        linkTokenRepository.save(token);

        return new TelegramLinkTokenResponse(
                "https://t.me/" + botUsername + "?start=" + rawToken,
                expiresAt
        );
    }

    /**
     * Telegram Webhook의 /start token 메시지를 처리해 서비스 사용자와 개인 chat_id를 연결한다.
     */
    @Transactional
    public TelegramUserLinkResponse linkByWebhook(TelegramWebhookCommand command) {
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
        return TelegramUserLinkResponse.from(userLinkRepository.save(link));
    }

    public TelegramUserLinkResponse getMyLink(Long userId) {
        return userLinkRepository.findByUserId(userId)
                .map(TelegramUserLinkResponse::from)
                .orElseThrow(() -> new BusinessException(TelegramErrorCode.TELEGRAM_USER_LINK_NOT_FOUND));
    }

    @Transactional
    public TelegramUserLinkResponse updateNotification(Long userId, UpdateTelegramNotificationCommand command) {
        TelegramUserLink link = userLinkRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(TelegramErrorCode.TELEGRAM_USER_LINK_NOT_FOUND));

        link.changeNotificationEnabled(command.enabled());
        return TelegramUserLinkResponse.from(link);
    }

    @Transactional
    public TelegramUserLinkResponse disconnect(Long userId) {
        TelegramUserLink link = userLinkRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(TelegramErrorCode.TELEGRAM_USER_LINK_NOT_FOUND));

        link.disconnect();
        return TelegramUserLinkResponse.from(link);
    }

    private void validateTelegramChatOwnership(Long userId, Long telegramChatId, Long telegramUserId) {
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
