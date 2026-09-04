package site.omagotchi.learningservice.telegram.application.port;

import site.omagotchi.learningservice.telegram.domain.TelegramUserLink;

import java.util.Optional;
import java.util.UUID;

/**
 * 조회는 모두 연동 중인 행만 돌려준다. 해제된 행은 이력으로 남을 뿐 어떤 판정에도 쓰이지 않는다.
 */
public interface TelegramUserLinkRepository {

    TelegramUserLink save(TelegramUserLink link);

    Optional<TelegramUserLink> findActiveByUserId(UUID userId);

    Optional<TelegramUserLink> findActiveByTelegramChatId(Long telegramChatId);

    Optional<TelegramUserLink> findActiveByTelegramUserId(Long telegramUserId);

}
