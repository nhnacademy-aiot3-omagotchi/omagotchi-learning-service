package site.omagotchi.learningservice.rule.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.learningservice.rule.domain.ThresholdRuleHistory;

public interface ThresholdRuleHistoryRepository extends JpaRepository<ThresholdRuleHistory, Long> {

}
