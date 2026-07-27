package site.omagotchi.learningservice.telegram.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.learningservice.telegram.domain.TelegramLinkToken;

import java.util.Optional;

public interface TelegramLinkTokenRepository extends JpaRepository<TelegramLinkToken, Long> {

    Optional<TelegramLinkToken> findByTokenHash(String tokenHash);
}
