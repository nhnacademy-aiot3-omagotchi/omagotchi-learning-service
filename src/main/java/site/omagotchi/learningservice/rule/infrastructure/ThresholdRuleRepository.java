package site.omagotchi.learningservice.rule.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.learningservice.rule.domain.ThresholdRule;

public interface ThresholdRuleRepository extends JpaRepository<ThresholdRule, Long> {
    boolean existsByDeviceEuiAndMetric(String deviceEui, String metric);
}
