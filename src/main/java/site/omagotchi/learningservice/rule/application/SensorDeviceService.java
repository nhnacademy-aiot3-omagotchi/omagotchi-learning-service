package site.omagotchi.learningservice.rule.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.rule.application.port.SensorDeviceRepository;
import site.omagotchi.learningservice.rule.application.result.SensorDeviceResult;
import site.omagotchi.learningservice.rule.domain.SensorDevice;

import java.util.Optional;
import java.util.*;

/** 센서 기기 마스터 조회. 다른 Feature는 이 서비스를 통해서만 접근한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SensorDeviceService {

    private final SensorDeviceRepository sensorDeviceRepository;

    /** 표시명. 등록되지 않은 기기면 비어 있다. */
    public Optional<String> findDisplayName(String deviceEui) {
        return sensorDeviceRepository.findByDeviceEui(deviceEui)
                .map(SensorDevice::getDisplayName);
    }

    public List<SensorDeviceResult> findAll(){
        List<SensorDevice> sensorDevices = sensorDeviceRepository.findAll();

        List<SensorDeviceResult> results = new ArrayList<>();
        for(SensorDevice device : sensorDevices){
            results.add(SensorDeviceResult.from(device));
        }

        return results;
    }

    public Map<String, String> findDisplayNames(){
        List<SensorDevice> devices = sensorDeviceRepository.findAll();

        Map<String, String> deviceMap = new HashMap<>();
        for(SensorDevice device : devices){
            String eui = device.getDeviceEui();
            String displayName = device.getDisplayName();

            if(Objects.isNull(displayName)){
                continue;
            }

            deviceMap.put(eui, displayName);
        }

        return deviceMap;
    }

}
