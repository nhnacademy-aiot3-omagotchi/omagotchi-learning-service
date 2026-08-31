package site.omagotchi.learningservice.sensor.infrastructure.space;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.sensor.application.port.SensorDeviceRepository;
import site.omagotchi.learningservice.space.application.port.SpaceReferenceQueryPort;

/** {@link SpaceReferenceQueryPort}의 센서 쪽 구현. */
@Component
@RequiredArgsConstructor
public class SensorSpaceReferenceQuery implements SpaceReferenceQueryPort {

    private final SensorDeviceRepository sensorDeviceRepository;

    @Override
    public long countSensors(Long spaceId) {
        return sensorDeviceRepository.countBySpaceId(spaceId);
    }
}
