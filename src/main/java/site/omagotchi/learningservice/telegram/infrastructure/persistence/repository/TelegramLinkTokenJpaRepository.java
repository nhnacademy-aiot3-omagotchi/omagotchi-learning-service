package site.omagotchi.learningservice.telegram.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.learningservice.telegram.domain.TelegramLinkToken;

import java.util.Optional;

public interface TelegramLinkTokenJpaRepository extends JpaRepository<TelegramLinkToken, Long> {

    Optional<TelegramLinkToken> findByTokenHash(String tokenHash);
}
