package site.omagotchi.learningservice.sensor.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.sensor.application.port.SensorPersistenceException;
import site.omagotchi.learningservice.sensor.application.port.SensorPersistenceException.Reason;
import site.omagotchi.learningservice.sensor.application.port.ThresholdRuleRepository;
import site.omagotchi.learningservice.sensor.domain.ThresholdRule;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;


@Repository
@RequiredArgsConstructor
public class ThresholdRuleJpaPersistence implements ThresholdRuleRepository {

    private static final String DUPLICATE_KEY = "23505";

    private final ThresholdRuleJpaRepository thresholdRuleJpaRepository;

    @Override
    public ThresholdRule save(ThresholdRule rule) {
        try {
            return thresholdRuleJpaRepository.saveAndFlush(rule);
        } catch (DataIntegrityViolationException e) {
            throw translate(e);
        }
    }

    /**
     * 유니크 위반만 경계 밖으로 번역한다.
     *
     * <p>DataIntegrityViolationException 은 FK·NOT NULL·CHECK 위반도 함께 묶는다.
     * threshold_rules 에는 기기 FK 와 metric·operator·version CHECK 가 걸려 있어, 전부
     * RULE_ALREADY_EXISTS 로 바꾸면 스키마나 우리 코드의 결함이 "이미 룰이 존재합니다"라는
     * 거짓 안내에 덮여 드러나지 않는다. SensorDeviceJpaPersistence 와 같은 방식이다.</p>
     */
    private RuntimeException translate(DataIntegrityViolationException exception) {
        if (DUPLICATE_KEY.equals(sqlStateOf(exception))) {
            return new SensorPersistenceException(
                    Reason.RULE_ALREADY_EXISTS,
                    exception.getMessage(),
                    exception
            );
        }

        // CHECK·NOT NULL·FK 위반은 우리 코드나 스키마의 문제다. 500 으로 드러나야 한다
        return exception;
    }

    private String sqlStateOf(DataIntegrityViolationException exception) {
        Throwable cause = exception.getMostSpecificCause();
        return cause instanceof SQLException sqlException ? sqlException.getSQLState() : null;
    }

    @Override
    public void update(ThresholdRule rule) {
        try {
            thresholdRuleJpaRepository.saveAndFlush(rule);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new SensorPersistenceException(
                    Reason.RULE_VERSION_CONFLICT,
                    e.getMessage(),
                    e
            );
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
    public Optional<ThresholdRule> findByDeviceEuiAndMetric(String deviceEui, String metric) {
        return thresholdRuleJpaRepository.findByDeviceEuiAndMetric(deviceEui, metric);
    }

    /**
     * 정렬을 붙이는 것이 의도다. PostgreSQL 은 순서를 보장하지 않아, 같은 데이터인데도
     * 호출마다 대표값이 달라질 수 있다 (공간 요약이 최빈값 동률일 때).
     */
    @Override
    public List<ThresholdRule> findByDeviceEuiIn(Collection<String> deviceEuis) {
        if (deviceEuis == null || deviceEuis.isEmpty()) {
            return List.of();
        }
        return thresholdRuleJpaRepository.findByDeviceEuiInOrderByDeviceEuiAsc(deviceEuis);
    }
}
