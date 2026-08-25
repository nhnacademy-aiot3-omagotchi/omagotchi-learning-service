package site.omagotchi.learningservice.rule.application.port;

import site.omagotchi.learningservice.rule.domain.ThresholdRule;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ThresholdRuleRepository {

    ThresholdRule save(ThresholdRule rule);

    void update(ThresholdRule rule);

    boolean existsByDeviceEuiAndMetric(String deviceEui, String metric);

    Optional<ThresholdRule> findById(Long ruleId);

    List<ThresholdRule> findAll();

    List<ThresholdRule> findByDeviceEuiIn(Collection<String> deviceEui);

    Optional<ThresholdRule> findByDeviceEuiAndMetric(String deviceEui, String metric);
}
