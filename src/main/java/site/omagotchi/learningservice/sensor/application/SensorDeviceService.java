package site.omagotchi.learningservice.sensor.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.sensor.application.command.CreateSensorDeviceCommand;
import site.omagotchi.learningservice.sensor.application.command.UpdateSensorDeviceCommand;
import site.omagotchi.learningservice.sensor.application.port.SensorDeviceRepository;
import site.omagotchi.learningservice.sensor.application.port.SensorPersistenceException;
import site.omagotchi.learningservice.sensor.application.result.SensorDeviceResult;
import site.omagotchi.learningservice.sensor.domain.SensorDevice;
import site.omagotchi.learningservice.space.application.SpaceCohortQueryService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 센서 기기 마스터 관리와 기수 범위 조회. 다른 Feature는 이 서비스를 통해서만 접근한다. */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SensorDeviceService {

    private final SensorDeviceRepository sensorDeviceRepository;
    private final CohortAccessService cohortAccessService;
    private final SpaceCohortQueryService spaceCohortQueryService;

    @Transactional
    public String create(Long cohortId, UUID requesterId, CreateSensorDeviceCommand command) {
        cohortAccessService.requireManager(cohortId, requesterId);
        requireSpaceInCohort(command.spaceId(), cohortId);

        SensorDevice device;
        try {
            device = SensorDevice.create(
                    command.deviceEui(),
                    command.spaceId(),
                    command.model(),
                    command.displayName(),
                    command.installationPoint(),
                    command.expectedIntervalSeconds(),
                    command.installedAt()
            );

        } catch (IllegalArgumentException e) {
            throw new BusinessException(SensorErrorCode.DEVICE_INVALID_ATTRIBUTE, e.getMessage(), e);
        }

        if (sensorDeviceRepository.existsByDeviceEui(device.getDeviceEui())) {
            throw new BusinessException(SensorErrorCode.DEVICE_ALREADY_EXISTS);
        }

        save(device);
        return device.getDeviceEui();
    }

    @Transactional
    public SensorDeviceResult update(
            Long cohortId,
            UUID requesterId,
            String deviceEui,
            UpdateSensorDeviceCommand command
    ) {
        cohortAccessService.requireManager(cohortId, requesterId);
        SensorDevice device = requireDeviceInCohort(deviceEui, cohortId);
        requireSpaceInCohort(command.spaceId(), cohortId);

        try {
            device.update(
                    command.spaceId(),
                    command.displayName(),
                    command.installationPoint(),
                    command.expectedIntervalSeconds(),
                    command.installedAt()
            );
        } catch (IllegalArgumentException e) {
            throw new BusinessException(SensorErrorCode.DEVICE_INVALID_ATTRIBUTE, e.getMessage(), e);
        }

        return SensorDeviceResult.from(save(device));
    }

    @Transactional
    public SensorDeviceResult changeActive(
            Long cohortId,
            UUID requesterId,
            String deviceEui,
            boolean active
    ) {
        cohortAccessService.requireManager(cohortId, requesterId);
        SensorDevice device = requireDeviceInCohort(deviceEui, cohortId);

        device.changeActive(active);
        return SensorDeviceResult.from(save(device));
    }

    private void requireSpaceInCohort(Long spaceId, Long cohortId) {
        boolean belongsToCohort = spaceCohortQueryService.findCohortId(spaceId)
                .filter(cohortId::equals)
                .isPresent();
        if (!belongsToCohort) {
            throw new BusinessException(SensorErrorCode.DEVICE_SPACE_NOT_FOUND);
        }
    }

    private SensorDevice requireDeviceInCohort(String deviceEui, Long cohortId) {
        SensorDevice device = sensorDeviceRepository.findByDeviceEui(deviceEui)
                .orElseThrow(() -> new BusinessException(SensorErrorCode.DEVICE_NOT_FOUND));

        boolean belongsToCohort = spaceCohortQueryService.findCohortId(device.getSpaceId())
                .filter(cohortId::equals)
                .isPresent();
        if (!belongsToCohort) {
            throw new BusinessException(SensorErrorCode.DEVICE_NOT_FOUND);
        }
        return device;
    }

    private SensorDevice save(SensorDevice device) {
        try {
            return sensorDeviceRepository.save(device);
        } catch (SensorPersistenceException exception) {
            SensorErrorCode errorCode = switch (exception.getReason()) {
                case DEVICE_EUI_ALREADY_EXISTS -> SensorErrorCode.DEVICE_ALREADY_EXISTS;
                case DEVICE_SPACE_NOT_FOUND -> SensorErrorCode.DEVICE_SPACE_NOT_FOUND;
                default -> throw exception;
            };
            throw new BusinessException(
                    errorCode,
                    exception.getMessage(),
                    originalCauseOf(exception)
            );
        }
    }

    private Throwable originalCauseOf(SensorPersistenceException exception) {
        return exception.getCause() == null ? exception : exception.getCause();
    }

    public Optional<Long> findSpaceId(String deviceEui) {
        return sensorDeviceRepository.findByDeviceEui(deviceEui)
                .map(SensorDevice::getSpaceId);
    }

    /**
     * 기수 범위 기기 목록.
     *
     * <p><b>읽기는 소속이면 된다.</b> 보안 경계는 기수이고, 그 안에서 자기 기수 센서가
     * 어디에 몇 대 있는지는 학생도 볼 수 있어야 대시보드가 그려진다. 경계는 쓰기에 있다
     * — 등록·수정·비활성화는 매니저만이다.</p>
     */
    public List<SensorDeviceResult> findAll(Long cohortId, UUID requesterId) {
        cohortAccessService.requireActiveMembershipId(cohortId, requesterId);
        List<Long> spaceIds = spaceCohortQueryService.findSpaceIdsByCohortId(cohortId);
        return sensorDeviceRepository.findBySpaceIds(spaceIds).stream()
                .map(SensorDeviceResult::from)
                .toList();
    }
}
