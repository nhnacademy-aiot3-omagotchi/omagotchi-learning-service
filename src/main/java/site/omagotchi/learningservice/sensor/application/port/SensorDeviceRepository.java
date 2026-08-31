package site.omagotchi.learningservice.sensor.application.port;


import site.omagotchi.learningservice.sensor.domain.SensorDevice;

import java.util.Collection;
import java.util.List;

import java.util.Optional;
/** 센서 관련 레포지토리 */
public interface SensorDeviceRepository {

    /** 특정 deviceEui를 가진 센서가 존재하는지 확인 */
    boolean existsByDeviceEui(String deviceEui);

    /** 센서 저장 */
    SensorDevice save(SensorDevice device);

    /** 특정 deviceEui로 센서 조회 */
    Optional<SensorDevice> findByDeviceEui(String deviceEui);

    /** 특정 공간에 할당된 센서들 조회 - 활성, 비활성 모두 */
    List<SensorDevice> findBySpaceIds(Collection<Long> spaceIds);

    /** 특정 공간에 할당된 센서들 조회 - 활성화된것만 */
    List<SensorDevice> findActiveBySpaceIds(Collection<Long> spaceIds);

}