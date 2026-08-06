package site.omagotchi.learningservice.gamification.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.learningservice.gamification.domain.XpSourceType;
import site.omagotchi.learningservice.gamification.domain.XpTransaction;

import java.util.Optional;

/**
 * 경험치 트랜잭션
 */
public interface XpTransactionRepository extends JpaRepository<XpTransaction, Long> {

    boolean existsBySourceTypeAndSourceId(XpSourceType sourceType, String sourceId);

    Optional<XpTransaction> findBySourceTypeAndSourceId(XpSourceType sourceType, String sourceId);
}
