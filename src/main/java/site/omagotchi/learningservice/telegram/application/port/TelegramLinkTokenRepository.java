package site.omagotchi.learningservice.telegram.application.port;

import site.omagotchi.learningservice.telegram.domain.TelegramLinkToken;

import java.util.Optional;

public interface TelegramLinkTokenRepository {

    TelegramLinkToken save(TelegramLinkToken token);

    Optional<TelegramLinkToken> findByTokenHash(String tokenHash);
}
