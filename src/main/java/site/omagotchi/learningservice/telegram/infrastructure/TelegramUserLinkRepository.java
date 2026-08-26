package site.omagotchi.learningservice.telegram.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.learningservice.telegram.domain.TelegramUserLink;

import java.util.Optional;
import java.util.UUID;

public interface TelegramUserLinkRepository extends JpaRepository<TelegramUserLink, Long> {

    Optional<TelegramUserLink> findByUserId(UUID userId);

    Optional<TelegramUserLink> findByTelegramChatId(Long telegramChatId);

    Optional<TelegramUserLink> findByTelegramUserId(Long telegramUserId);

    Optional<TelegramUserLink> findByUserIdAndDisconnectedAtIsNull(UUID userId);
}
