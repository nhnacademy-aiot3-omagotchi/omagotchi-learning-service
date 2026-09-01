package site.omagotchi.learningservice.sensor.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import site.omagotchi.learningservice.sensor.application.port.SensorPersistenceException;
import site.omagotchi.learningservice.sensor.domain.SensorDevice;

import java.sql.SQLException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SensorDeviceJpaPersistenceTest {

    @Mock
    private SensorDeviceJpaRepository repository;

    @Test
    void translatesDuplicateKeyWithoutDependingOnApplicationErrorCode() {
        SensorDevice device = SensorDevice.create(
                "0011223344556677",
                10L,
                "AM103",
                "환경 센서",
                null,
                60,
                Instant.parse("2026-08-30T00:00:00Z")
        );
        SQLException sqlException = new SQLException("duplicate", "23505");
        DataIntegrityViolationException persistenceFailure =
                new DataIntegrityViolationException("duplicate", sqlException);
        when(repository.saveAndFlush(device)).thenThrow(persistenceFailure);

        SensorDeviceJpaPersistence adapter = new SensorDeviceJpaPersistence(repository);

        assertThatThrownBy(() -> adapter.save(device))
                .isInstanceOfSatisfying(
                        SensorPersistenceException.class,
                        exception -> {
                            assertThat(exception.getReason())
                                    .isEqualTo(SensorPersistenceException.Reason.DEVICE_EUI_ALREADY_EXISTS);
                            assertThat(exception.getCause())
                                    .isSameAs(persistenceFailure);
                        }
                );
    }
}
