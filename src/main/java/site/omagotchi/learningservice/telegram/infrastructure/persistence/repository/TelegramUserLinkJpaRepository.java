package site.omagotchi.learningservice.telegram.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.learningservice.telegram.domain.TelegramUserLink;

import java.util.Optional;
import java.util.UUID;

/**
 * 세 조회 모두 {@code disconnected_at IS NULL}로 좁힌다.
 *
 * <p>부분 유니크(V23) 이후 한 사용자·챗·텔레그램 계정에 해제된 행이 여럿 쌓일 수 있어,
 * 좁히지 않으면 {@code Optional} 반환이 복수 결과로 깨진다.</p>
 */
public interface TelegramUserLinkJpaRepository extends JpaRepository<TelegramUserLink, Long> {

    Optional<TelegramUserLink> findByUserIdAndDisconnectedAtIsNull(UUID userId);

    Optional<TelegramUserLink> findByTelegramChatIdAndDisconnectedAtIsNull(Long telegramChatId);

    Optional<TelegramUserLink> findByTelegramUserIdAndDisconnectedAtIsNull(Long telegramUserId);
}
