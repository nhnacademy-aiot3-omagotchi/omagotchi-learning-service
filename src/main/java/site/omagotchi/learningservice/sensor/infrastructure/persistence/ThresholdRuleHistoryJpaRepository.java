package site.omagotchi.learningservice.sensor.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.learningservice.sensor.domain.ThresholdRuleHistory;

public interface ThresholdRuleHistoryJpaRepository extends JpaRepository<ThresholdRuleHistory, Long> {
}
