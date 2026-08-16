package site.omagotchi.learningservice.rule.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.omagotchi.learningservice.rule.application.port.SensorDeviceRepository;
import site.omagotchi.learningservice.rule.application.result.SensorDeviceResult;
import site.omagotchi.learningservice.rule.domain.SensorDevice;

import java.util.*;

@RequiredArgsConstructor
@Service
public class SensorDeviceService {

    private final SensorDeviceRepository sensorDeviceRepository;

    public List<SensorDeviceResult> findAll(){
        List<SensorDevice> sensorDevices = sensorDeviceRepository.findAll();

        List<SensorDeviceResult> results = new ArrayList<>();
        for(SensorDevice device : sensorDevices){
            results.add(SensorDeviceResult.from(device));
        }

        return results;
    }

    public Map<String, String> findDisplayName(Collection<String> deviceEuis){
        List<SensorDevice> devices = sensorDeviceRepository.findByDeviceEuiIn(deviceEuis);

        Map<String, String> deviceMap = new HashMap<>();
        for(SensorDevice device : devices){
            String eui = device.getDeviceEui();
            String displayName = device.getDisplayName();

            if(Objects.isNull(displayName)){
                break;
            }

            deviceMap.put(eui, displayName);
        }

        return deviceMap;
    }

}
