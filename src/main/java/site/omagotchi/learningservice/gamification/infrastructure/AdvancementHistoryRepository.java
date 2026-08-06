package site.omagotchi.learningservice.gamification.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.learningservice.gamification.domain.AdvancementHistory;
import site.omagotchi.learningservice.gamification.domain.AdvancementStage;

/**
 * 전직 이력
 */
public interface AdvancementHistoryRepository extends JpaRepository<AdvancementHistory, Long> {

    boolean existsByUserCharacterIdAndStage(Long userCharacterId, AdvancementStage stage);
}
