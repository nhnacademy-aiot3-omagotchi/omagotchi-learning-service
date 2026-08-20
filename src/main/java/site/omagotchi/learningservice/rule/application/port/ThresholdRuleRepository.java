package site.omagotchi.learningservice.rule.application.port;

import site.omagotchi.learningservice.rule.domain.ThresholdRule;

import java.util.List;
import java.util.Optional;

public interface ThresholdRuleRepository {

    ThresholdRule save(ThresholdRule rule);

    void update(ThresholdRule rule);

    boolean existsByDeviceEuiAndMetric(String deviceEui, String metric);

    Optional<ThresholdRule> findById(Long ruleId);

    List<ThresholdRule> findAll();

    Optional<ThresholdRule> findByDeviceEuiAndMetric(String deviceEui, String metric);
}
