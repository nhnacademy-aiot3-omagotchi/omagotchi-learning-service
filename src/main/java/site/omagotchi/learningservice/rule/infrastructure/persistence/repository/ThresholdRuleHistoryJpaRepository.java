package site.omagotchi.learningservice.rule.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.learningservice.rule.domain.ThresholdRuleHistory;

public interface ThresholdRuleHistoryJpaRepository extends JpaRepository<ThresholdRuleHistory, Long> {
}
