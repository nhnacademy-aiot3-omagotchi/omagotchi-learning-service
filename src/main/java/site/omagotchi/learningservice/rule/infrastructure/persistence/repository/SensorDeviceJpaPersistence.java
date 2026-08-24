package site.omagotchi.learningservice.rule.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.rule.application.port.SensorDeviceRepository;
import site.omagotchi.learningservice.rule.domain.SensorDevice;

import java.util.Optional;
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
    public Optional<SensorDevice> findByDeviceEui(String deviceEui) {
        return sensorDeviceJpaRepository.findById(deviceEui);
    }

    @Override
    public List<SensorDevice> findAllActive() {
        return sensorDeviceJpaRepository.findByActiveTrue();
    }
}
