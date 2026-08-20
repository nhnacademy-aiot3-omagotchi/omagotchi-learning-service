package site.omagotchi.learningservice.rule.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.omagotchi.learningservice.rule.application.port.SensorDeviceRepository;
import site.omagotchi.learningservice.rule.domain.SensorDevice;

import java.util.Optional;

/** 센서 기기 마스터 조회. 다른 Feature는 이 서비스를 통해서만 접근한다. */
@Service
@RequiredArgsConstructor
public class SensorDeviceService {

    private final SensorDeviceRepository sensorDeviceRepository;

    /** 표시명. 등록되지 않은 기기면 비어 있다. */
    public Optional<String> findDisplayName(String deviceEui) {
        return sensorDeviceRepository.findByDeviceEui(deviceEui)
                .map(SensorDevice::getDisplayName);
    }
}