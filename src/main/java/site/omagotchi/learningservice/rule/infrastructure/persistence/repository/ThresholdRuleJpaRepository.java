package site.omagotchi.learningservice.rule.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.learningservice.rule.domain.ThresholdRule;

public interface ThresholdRuleJpaRepository extends JpaRepository<ThresholdRule, Long> {
    boolean existsByDeviceEuiAndMetric(String deviceEui, String metric);
}
