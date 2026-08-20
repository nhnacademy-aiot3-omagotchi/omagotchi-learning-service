package site.omagotchi.learningservice.rule.application.port;


import site.omagotchi.learningservice.rule.domain.SensorDevice;

import java.util.List;

import java.util.Optional;

public interface SensorDeviceRepository {

    boolean existsByDeviceEui(String deviceEui);

    Optional<SensorDevice> findByDeviceEui(String deviceEui);

    List<SensorDevice> findAll();
}
