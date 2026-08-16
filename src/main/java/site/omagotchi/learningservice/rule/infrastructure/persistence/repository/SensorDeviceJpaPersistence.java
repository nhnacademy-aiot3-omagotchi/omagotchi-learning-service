package site.omagotchi.learningservice.rule.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.rule.application.port.SensorDeviceRepository;
import site.omagotchi.learningservice.rule.domain.SensorDevice;

import java.util.Collection;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class SensorDeviceJpaPersistence implements SensorDeviceRepository {

    private final SensorDeviceJpaRepository sensorDeviceJpaRepository;

    @Override
    public boolean existsByDeviceEui(String deviceEui) {
        return sensorDeviceJpaRepository.existsById(deviceEui);
    }

    @Override
    public List<SensorDevice> findAll() {
        return sensorDeviceJpaRepository.findAll();
    }

    @Override
    public List<SensorDevice> findByDeviceEuiIn(Collection<String> deviceEuis) {
        return sensorDeviceJpaRepository.findByDeviceEuiIn(deviceEuis);
    }
}
