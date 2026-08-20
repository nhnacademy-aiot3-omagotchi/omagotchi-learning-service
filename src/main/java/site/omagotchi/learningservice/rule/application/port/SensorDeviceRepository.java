package site.omagotchi.learningservice.rule.application.port;


import site.omagotchi.learningservice.rule.domain.SensorDevice;

import java.util.List;

public interface SensorDeviceRepository {

    boolean existsByDeviceEui(String deviceEui);

    List<SensorDevice> findAll();
}
