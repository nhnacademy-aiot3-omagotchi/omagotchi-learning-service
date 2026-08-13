package site.omagotchi.learningservice.rule.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.rule.application.RuleErrorCode;
import site.omagotchi.learningservice.rule.application.port.ThresholdRuleRepository;
import site.omagotchi.learningservice.rule.domain.ThresholdRule;

import java.util.List;
import java.util.Optional;


@Repository
@RequiredArgsConstructor
public class ThresholdRuleJpaPersistence implements ThresholdRuleRepository {

    private final ThresholdRuleJpaRepository thresholdRuleJpaRepository;

    @Override
    public ThresholdRule save(ThresholdRule rule) {
        try {
            return thresholdRuleJpaRepository.saveAndFlush(rule);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(RuleErrorCode.RULE_ALREADY_EXISTS, e.getMessage(), e);
        }
    }

    @Override
    public void update(ThresholdRule rule) {
        try {
            thresholdRuleJpaRepository.saveAndFlush(rule);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException(RuleErrorCode.RULE_VERSION_CONFLICT, e.getMessage(), e);
        }
    }

    @Override
    public boolean existsByDeviceEuiAndMetric(String deviceEui, String metric) {
        return thresholdRuleJpaRepository.existsByDeviceEuiAndMetric(deviceEui, metric);
    }

    @Override
    public Optional<ThresholdRule> findById(Long ruleId) {
        return thresholdRuleJpaRepository.findById(ruleId);
    }

    @Override
    public List<ThresholdRule> findAll() {
        return thresholdRuleJpaRepository.findAll();
    }
}
