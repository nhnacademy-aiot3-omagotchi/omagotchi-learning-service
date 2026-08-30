package site.omagotchi.learningservice.sensor.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.sensor.application.command.CreateSensorDeviceCommand;
import site.omagotchi.learningservice.sensor.application.command.UpdateSensorDeviceCommand;
import site.omagotchi.learningservice.sensor.application.port.SensorDeviceRepository;
import site.omagotchi.learningservice.sensor.application.result.SensorDeviceResult;
import site.omagotchi.learningservice.sensor.domain.SensorDevice;
import site.omagotchi.learningservice.space.application.SpaceAccessService;

import java.util.*;

/** 센서 기기 마스터 조회. 다른 Feature는 이 서비스를 통해서만 접근한다. */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SensorDeviceService {

    private final SensorDeviceRepository sensorDeviceRepository;
    private final SpaceAccessService spaceAccessService;

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
            throw new BusinessException(SensorErrorCode.DEVICE_INVALID_ATTRIBUTE, e.getMessage(), e);
        }

        if(sensorDeviceRepository.existsByDeviceEui(device.getDeviceEui())){
            throw new BusinessException(SensorErrorCode.DEVICE_ALREADY_EXISTS);
        }

        requireSpaceExists(device.getSpaceId());

        sensorDeviceRepository.save(device);
        return device.getDeviceEui();
    }

    @Transactional
    public SensorDeviceResult update(UpdateSensorDeviceCommand command){
        SensorDevice device = sensorDeviceRepository.findByDeviceEui(command.deviceEui())
                .orElseThrow(() -> new BusinessException(SensorErrorCode.DEVICE_NOT_FOUND));

        try{
            device.update(
                    command.spaceId(),
                    command.displayName(),
                    command.installationPoint(),
                    command.expectedIntervalSeconds(),
                    command.installedAt()
            );
        }catch (IllegalArgumentException e){
            throw new BusinessException(SensorErrorCode.DEVICE_INVALID_ATTRIBUTE, e.getMessage(), e);
        }

        requireSpaceExists(device.getSpaceId());

        return SensorDeviceResult.from(sensorDeviceRepository.save(device));
    }

    @Transactional
    public SensorDeviceResult changeActive(String deviceEui, boolean active){
        SensorDevice device = sensorDeviceRepository.findByDeviceEui(deviceEui)
                .orElseThrow(() -> new BusinessException(SensorErrorCode.DEVICE_NOT_FOUND));

        device.changeActive(active);
        return SensorDeviceResult.from(device);
    }


    private void requireSpaceExists(Long spaceId){
        if(Objects.isNull(spaceId)){
            return;
        }

        if(spaceAccessService.find(spaceId).isEmpty()){
            throw new BusinessException(SensorErrorCode.DEVICE_SPACE_NOT_FOUND);
        }
    }

    /**
     * 이 기기가 설치된 공간. 조치 알림의 수신자 판정이 소비처다.
     *
     * <p>등록되지 않은 기기이거나 공간이 배정되지 않은 기기면 비어 있다. 소비처는 둘을
     * 구분하지 않아도 된다 — 어느 쪽이든 "이 기기가 어느 공간 것인지 말할 수 없다"는
     * 같은 결론이다.</p>
     */
    public Optional<Long> findSpaceId(String deviceEui) {
        return sensorDeviceRepository.findByDeviceEui(deviceEui)
                .map(SensorDevice::getSpaceId);
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

    /** 운영 중인 기기의 EUI 집합. 대시보드 집계와 룰 대상은 이 목록으로 제한한다. */
    public Set<String> findActiveDeviceEuis() {
        Set<String> euis = new HashSet<>();

        for (SensorDevice device : sensorDeviceRepository.findAllActive()) {
            euis.add(device.getDeviceEui());
        }

        return euis;
    }

}
