package site.omagotchi.learningservice.telegram.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.telegram.application.port.TelegramLinkTokenRepository;
import site.omagotchi.learningservice.telegram.domain.TelegramLinkToken;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class TelegramLinkTokenJpaPersistence implements TelegramLinkTokenRepository {

    private final TelegramLinkTokenJpaRepository repository;

    @Override
    public TelegramLinkToken save(TelegramLinkToken token) {
        return repository.save(token);
    }

    @Override
    public Optional<TelegramLinkToken> findByTokenHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash);
    }
}
