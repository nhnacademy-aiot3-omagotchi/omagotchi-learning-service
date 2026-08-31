package site.omagotchi.learningservice.sensor.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import site.omagotchi.learningservice.sensor.application.port.SensorPersistenceException;
import site.omagotchi.learningservice.sensor.domain.Operator;
import site.omagotchi.learningservice.sensor.domain.ThresholdRule;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
}
