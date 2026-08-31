package site.omagotchi.learningservice.telegram.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.learningservice.telegram.domain.TelegramUserLink;

import java.util.Optional;
import java.util.UUID;

public interface TelegramUserLinkJpaRepository extends JpaRepository<TelegramUserLink, Long> {

    Optional<TelegramUserLink> findByUserId(UUID userId);

    Optional<TelegramUserLink> findByTelegramChatId(Long telegramChatId);

    Optional<TelegramUserLink> findByTelegramUserId(Long telegramUserId);
}
