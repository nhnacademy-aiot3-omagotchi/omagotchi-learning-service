package site.omagotchi.learningservice.rule.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.rule.application.port.SensorDeviceRepository;

@Repository
@RequiredArgsConstructor
public class SensorDeviceJpaPersistence implements SensorDeviceRepository {

    private final SensorDeviceJpaRepository sensorDeviceJpaRepository;

    @Override
    public boolean existsByDeviceEui(String deviceEui) {
        return sensorDeviceJpaRepository.existsById(deviceEui);
    }
}
