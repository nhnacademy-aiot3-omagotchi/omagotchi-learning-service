package site.omagotchi.learningservice.telegram.application.port;

import site.omagotchi.learningservice.telegram.domain.TelegramUserLink;

import java.util.Optional;
import java.util.UUID;

public interface TelegramUserLinkRepository {

    TelegramUserLink save(TelegramUserLink link);

    Optional<TelegramUserLink> findByUserId(UUID userId);

    Optional<TelegramUserLink> findByTelegramChatId(Long telegramChatId);

    Optional<TelegramUserLink> findByTelegramUserId(Long telegramUserId);

}
