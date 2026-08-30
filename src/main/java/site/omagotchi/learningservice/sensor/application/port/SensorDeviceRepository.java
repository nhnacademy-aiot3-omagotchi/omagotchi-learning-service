package site.omagotchi.learningservice.sensor.application.port;


import site.omagotchi.learningservice.sensor.domain.SensorDevice;

import java.util.List;

import java.util.Optional;

public interface SensorDeviceRepository {

    boolean existsByDeviceEui(String deviceEui);

    SensorDevice save(SensorDevice device);

    Optional<SensorDevice> findByDeviceEui(String deviceEui);

    ///공간별 센서 리스트 조회
    List<SensorDevice> findBySpaceId(Long spaceId);

    ///공간이 배정된 센서 전체 리스트 조회
    List<SensorDevice> findAllWithSpace();

    List<SensorDevice> findAll();

    List<SensorDevice> findAllActive();
}
