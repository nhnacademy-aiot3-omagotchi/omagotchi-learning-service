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

    /**
     * 인계 전용 조회. 행 잠금을 잡는다.
     *
     * <p>SensorDevice 에는 {@code @Version} 이 없다. 잠그지 않으면 두 기수 매니저가 같은
     * 고아 센서를 동시에 인계할 때 둘 다 고아 판정을 통과하고 나중 커밋이 이긴다. 진 쪽도
     * 200 을 받으므로 아무도 실패를 모른 채 센서는 한 기수에만 남는다.</p>
     */
    Optional<SensorDevice> findByDeviceEuiForUpdate(String deviceEui);

    /** 특정 공간에 할당된 센서들 조회 - 활성, 비활성 모두 */
    List<SensorDevice> findBySpaceIds(Collection<Long> spaceIds);

    /** 특정 공간에 배치된 센서 수. 공간 삭제 가능 여부 판정에 쓴다. */
    long countBySpaceId(Long spaceId);

    /** 특정 공간에 할당된 센서들 조회 - 활성화된것만 */
    List<SensorDevice> findActiveBySpaceIds(Collection<Long> spaceIds);

}