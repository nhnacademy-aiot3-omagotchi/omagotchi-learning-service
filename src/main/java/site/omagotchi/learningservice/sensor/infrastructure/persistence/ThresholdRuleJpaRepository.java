package site.omagotchi.learningservice.sensor.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.learningservice.sensor.domain.ThresholdRule;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ThresholdRuleJpaRepository extends JpaRepository<ThresholdRule, Long> {
    boolean existsByDeviceEuiAndMetric(String deviceEui, String metric);

    Optional<ThresholdRule> findByDeviceEuiAndMetric(String deviceEui, String metric);
    
    List<ThresholdRule> findByDeviceEuiInOrderByDeviceEuiAsc(Collection<String> deviceEuis);
}
