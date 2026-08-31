package site.omagotchi.learningservice.sensor.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.sensor.application.port.SensorDeviceRepository;
import site.omagotchi.learningservice.sensor.domain.SensorDevice;
import site.omagotchi.learningservice.space.application.SpaceCohortQueryService;

import java.util.List;

/**
 * 기수 종료 시 그 기수 공간의 센서를 회수한다 (CE-05).
 *
 * <p><b>공간 관리 주체 해제(CE-04)보다 반드시 먼저다.</b> 뒤집히면 {@code spaces.cohort_id}가
 * 이미 NULL이라 대상 공간을 하나도 찾지 못한다. 그러면 센서는 어느 기수에서도 보이지 않고
 * 수정·비활성화도 막힌 채 룰만 계속 발화한다.</p>
 *
 * <p>{@code CohortEndedSpaceCleanup}과 나눈 이유는 소속 Feature가 다르기 때문이다.
 * 행위자가 없는 시스템 사건이 근거라는 성격은 저쪽과 같다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CohortEndedSensorCleanup {

    private final SensorDeviceRepository sensorDeviceRepository;
    private final SpaceCohortQueryService spaceCohortQueryService;

    /**
     * 센서 회수. 같은 기수에 두 번 호출해도 안전하다 — 두 번째는 활성 대상이 없다.
     *
     * @return 회수한 센서 수
     */
    @Transactional
    public int deactivateSensors(Long endedCohortId) {
        List<Long> spaceIds = spaceCohortQueryService.findSpaceIdsByCohortId(endedCohortId);
        List<SensorDevice> devices = sensorDeviceRepository.findActiveBySpaceIds(spaceIds);

        for (SensorDevice device : devices) {
            device.changeActive(false);
            sensorDeviceRepository.save(device);
        }

        if (!devices.isEmpty()) {
            log.info("기수 종료로 센서를 회수했습니다. cohortId={}, 회수={}대",
                    endedCohortId, devices.size());
        }
        return devices.size();
    }
}
