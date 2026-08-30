package site.omagotchi.learningservice.sensor.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.sensor.domain.SensorErrorCode;
import site.omagotchi.learningservice.sensor.application.port.ThresholdRuleRepository;
import site.omagotchi.learningservice.sensor.domain.ThresholdRule;

import java.util.Collection;
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
            throw new BusinessException(SensorErrorCode.RULE_ALREADY_EXISTS, e.getMessage(), e);
        }
    }

    @Override
    public void update(ThresholdRule rule) {
        try {
            thresholdRuleJpaRepository.saveAndFlush(rule);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new BusinessException(SensorErrorCode.RULE_VERSION_CONFLICT, e.getMessage(), e);
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

    @Override
    public Optional<ThresholdRule> findByDeviceEuiAndMetric(String deviceEui, String metric) {
        return thresholdRuleJpaRepository.findByDeviceEuiAndMetric(deviceEui, metric);
    }

    /**
     * 정렬을 붙이는 것이 의도다. PostgreSQL 은 순서를 보장하지 않아, 같은 데이터인데도
     * 호출마다 대표값이 달라질 수 있다 (공간 요약이 최빈값 동률일 때).
     */
    @Override
    public List<ThresholdRule> findByDeviceEuiIn(Collection<String> deviceEuis) {
        return thresholdRuleJpaRepository.findByDeviceEuiInOrderByDeviceEuiAsc(deviceEuis);
    }
}
