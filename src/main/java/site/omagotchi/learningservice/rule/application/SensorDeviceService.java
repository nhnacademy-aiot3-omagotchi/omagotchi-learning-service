package site.omagotchi.learningservice.rule.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.rule.application.command.CreateSensorDeviceCommand;
import site.omagotchi.learningservice.rule.application.command.UpdateSensorDeviceCommand;
import site.omagotchi.learningservice.rule.application.port.SensorDeviceRepository;
import site.omagotchi.learningservice.rule.application.result.SensorDeviceResult;
import site.omagotchi.learningservice.rule.domain.SensorDevice;

import java.util.*;

/** 센서 기기 마스터 조회. 다른 Feature는 이 서비스를 통해서만 접근한다. */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SensorDeviceService {

    private final SensorDeviceRepository sensorDeviceRepository;

    @Transactional
    public String create(CreateSensorDeviceCommand command){
        SensorDevice device;
        try{
            device = SensorDevice.create(
                    command.deviceEui(),
                    command.spaceId(),
                    command.model(),
                    command.displayName(),
                    command.installationPoint(),
                    command.expectedIntervalSeconds(),
                    command.installedAt()
            );

        }catch (IllegalArgumentException e){
            throw new BusinessException(RuleErrorCode.DEVICE_INVALID_ATTRIBUTE, e.getMessage());
        }

        if(sensorDeviceRepository.existsByDeviceEui(device.getDeviceEui())){
            throw new BusinessException(RuleErrorCode.DEVICE_INVALID_ATTRIBUTE);
        }

        return device.getDeviceEui();
    }

    @Transactional
    public SensorDeviceResult update(UpdateSensorDeviceCommand command){
        SensorDevice device = sensorDeviceRepository.findByDeviceEui(command.deviceEui())
                .orElseThrow(() -> new BusinessException(RuleErrorCode.DEVICE_NOT_FOUND));

        try{
            device.update(
                    command.spaceId(),
                    command.displayName(),
                    command.installationPoint(),
                    command.expectedIntervalSeconds(),
                    command.installedAt()
            );
        }catch (IllegalArgumentException e){
            throw new BusinessException(RuleErrorCode.DEVICE_INVALID_ATTRIBUTE);
        }

        return SensorDeviceResult.from(sensorDeviceRepository.save(device));
    }

    @Transactional
    public SensorDeviceResult changeActive(String deviceEui, boolean active){
        SensorDevice device = sensorDeviceRepository.findByDeviceEui(deviceEui)
                .orElseThrow(() -> new BusinessException(RuleErrorCode.DEVICE_NOT_FOUND));

        device.changeActive(active);
        return SensorDeviceResult.from(device);
    }

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
