package site.omagotchi.learningservice.rule.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.learningservice.rule.domain.ThresholdRule;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ThresholdRuleJpaRepository extends JpaRepository<ThresholdRule, Long> {
    boolean existsByDeviceEuiAndMetric(String deviceEui, String metric);

    Optional<ThresholdRule> findByDeviceEuiAndMetric(String deviceEui, String metric);
    
    List<ThresholdRule> findByDeviceEuiInOrderByDeviceEuiAsc(Collection<String> deviceEuis);
}
