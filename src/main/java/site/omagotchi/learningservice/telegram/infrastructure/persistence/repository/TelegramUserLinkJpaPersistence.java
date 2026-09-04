package site.omagotchi.learningservice.telegram.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.telegram.application.port.TelegramUserLinkRepository;
import site.omagotchi.learningservice.telegram.domain.TelegramUserLink;
import site.omagotchi.learningservice.telegram.infrastructure.persistence.TelegramConstraintTranslator;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TelegramUserLinkJpaPersistence implements TelegramUserLinkRepository {

    private final TelegramUserLinkJpaRepository userLinkJpaRepository;

    /**
     * <p><b>{@code saveAndFlush}인 것이 중요하다.</b> {@code save}만 부르면 INSERT가 커밋
     * 시점으로 밀려 이 try 블록 밖에서 터진다 — 변환이 한 번도 돌지 않는다
     * ({@code OccupancyParticipantJpaPersistence}와 같은 이유).</p>
     */
    @Override
    public TelegramUserLink save(TelegramUserLink link) {
        try {
            return userLinkJpaRepository.saveAndFlush(link);
        } catch (DataIntegrityViolationException e) {
            throw TelegramConstraintTranslator.translate(e);
        }
    }

    @Override
    public Optional<TelegramUserLink> findActiveByUserId(UUID userId) {
        return userLinkJpaRepository.findByUserIdAndDisconnectedAtIsNull(userId);
    }

    @Override
    public Optional<TelegramUserLink> findActiveByTelegramChatId(Long telegramChatId) {
        return userLinkJpaRepository.findByTelegramChatIdAndDisconnectedAtIsNull(telegramChatId);
    }

    @Override
    public Optional<TelegramUserLink> findActiveByTelegramUserId(Long telegramUserId) {
        return userLinkJpaRepository.findByTelegramUserIdAndDisconnectedAtIsNull(telegramUserId);
    }
}
