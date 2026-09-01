package site.omagotchi.learningservice.sensor.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import site.omagotchi.learningservice.sensor.application.port.SensorPersistenceException;
import site.omagotchi.learningservice.sensor.domain.Operator;
import site.omagotchi.learningservice.sensor.domain.ThresholdRule;

import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThresholdRuleJpaPersistenceTest {

    @Mock
    private ThresholdRuleJpaRepository repository;

    @Test
    void preservesOptimisticLockFailureAsPortExceptionCause() {
        ThresholdRule rule = ThresholdRule.create(
                "0011223344556677",
                "co2",
                Operator.GT,
                1000.0,
                UUID.fromString("00000000-0000-0000-0000-000000000001")
        );
        ObjectOptimisticLockingFailureException persistenceFailure =
                new ObjectOptimisticLockingFailureException(ThresholdRule.class, 1L);
        when(repository.saveAndFlush(rule)).thenThrow(persistenceFailure);

        ThresholdRuleJpaPersistence adapter = new ThresholdRuleJpaPersistence(repository);

        assertThatThrownBy(() -> adapter.update(rule))
                .isInstanceOfSatisfying(
                        SensorPersistenceException.class,
                        exception -> {
                            assertThat(exception.getReason())
                                    .isEqualTo(SensorPersistenceException.Reason.RULE_VERSION_CONFLICT);
                            assertThat(exception.getCause())
                                    .isSameAs(persistenceFailure);
                        }
                );
    }

    @Test
    void translatesOnlyUniqueViolationIntoRuleAlreadyExists() {
        DataIntegrityViolationException duplicate = new DataIntegrityViolationException(
                "uq_threshold_rules_device_metric",
                new SQLException("duplicate key", "23505")
        );
        when(repository.saveAndFlush(any(ThresholdRule.class))).thenThrow(duplicate);

        ThresholdRuleJpaPersistence adapter = new ThresholdRuleJpaPersistence(repository);

        assertThatThrownBy(() -> adapter.save(rule()))
                .isInstanceOfSatisfying(SensorPersistenceException.class, exception -> {
                    assertThat(exception.getReason())
                            .isEqualTo(SensorPersistenceException.Reason.RULE_ALREADY_EXISTS);
                    assertThat(exception.getCause()).isSameAs(duplicate);
                });
    }

    @Test
    void leavesNonUniqueIntegrityViolationAsIs() {
        // CHECK 위반이 "이미 룰이 존재합니다"로 둔갑하면 스키마 결함이 조용히 묻힌다
        DataIntegrityViolationException checkViolation = new DataIntegrityViolationException(
                "ck_threshold_rules_metric",
                new SQLException("check violation", "23514")
        );
        when(repository.saveAndFlush(any(ThresholdRule.class))).thenThrow(checkViolation);

        ThresholdRuleJpaPersistence adapter = new ThresholdRuleJpaPersistence(repository);

        assertThatThrownBy(() -> adapter.save(rule())).isSameAs(checkViolation);
    }

    private ThresholdRule rule() {
        return ThresholdRule.create(
                "0011223344556677",
                "co2",
                Operator.GT,
                1000.0,
                UUID.fromString("00000000-0000-0000-0000-000000000001")
        );
    }
}
