package site.omagotchi.learningservice.sensor.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.sensor.application.port.SensorDeviceRepository;
import site.omagotchi.learningservice.sensor.application.port.SensorPersistenceException;
import site.omagotchi.learningservice.sensor.application.port.SensorPersistenceException.Reason;
import site.omagotchi.learningservice.sensor.domain.SensorDevice;

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class SensorDeviceJpaPersistence implements SensorDeviceRepository {

    /** unique_violation. sensor_devices 의 유일한 UNIQUE 는 PK(device_eui) 다 */
    private static final String DUPLICATE_KEY = "23505";

    /** foreign_key_violation. 이 테이블의 유일한 FK 는 space_id 다 */
    private static final String FOREIGN_KEY_VIOLATION = "23503";

    private final SensorDeviceJpaRepository sensorDeviceJpaRepository;

    /**
     * DataIntegrityViolationException 은 중복·FK·CHECK·NOT NULL 을 모두 묶은 상위 예외다.
     * 통째로 입력 오류로 바꾸면 서버 결함(예: NOT NULL 위반)까지 사용자 탓으로 응답하게 되므로,
     * 의미를 아는 두 가지만 변환하고 나머지는 그대로 올린다.
     *
     * <p>서비스가 이미 중복 EUI 와 공간 실재를 확인하므로, 이 경로는 동시 요청 경합에서만
     * 탄다. 그래서 원인 예외를 반드시 전달해야 원인을 추적할 수 있다.</p>
     */
    @Override
    public SensorDevice save(SensorDevice device) {
        try {
            return sensorDeviceJpaRepository.saveAndFlush(device);
        } catch (DataIntegrityViolationException e) {
            throw translate(e);
        }
    }

    private RuntimeException translate(DataIntegrityViolationException exception) {
        String sqlState = sqlStateOf(exception);

        if (DUPLICATE_KEY.equals(sqlState)) {
            return new SensorPersistenceException(
                    Reason.DEVICE_EUI_ALREADY_EXISTS,
                    exception.getMessage(),
                    exception
            );
        }

        if (FOREIGN_KEY_VIOLATION.equals(sqlState)) {
            return new SensorPersistenceException(
                    Reason.DEVICE_SPACE_NOT_FOUND,
                    exception.getMessage(),
                    exception
            );
        }

        // CHECK·NOT NULL 위반은 우리 코드나 스키마의 문제다. 500 으로 드러나야 한다
        return exception;
    }

    private String sqlStateOf(DataIntegrityViolationException exception) {
        Throwable cause = exception.getMostSpecificCause();
        return cause instanceof SQLException sqlException ? sqlException.getSQLState() : null;
    }

    @Override
    public boolean existsByDeviceEui(String deviceEui) {
        return sensorDeviceJpaRepository.existsById(deviceEui);
    }

    @Override
    public Optional<SensorDevice> findByDeviceEui(String deviceEui) {
        return sensorDeviceJpaRepository.findById(deviceEui);
    }

    @Override
    public List<SensorDevice> findBySpaceIds(Collection<Long> spaceIds) {
        if (spaceIds == null || spaceIds.isEmpty()) {
            return List.of();
        }
        return sensorDeviceJpaRepository
                .findBySpaceIdInOrderBySpaceIdAscDeviceEuiAsc(spaceIds);
    }

    @Override
    public List<SensorDevice> findActiveBySpaceIds(Collection<Long> spaceIds) {
        if (spaceIds == null || spaceIds.isEmpty()) {
            return List.of();
        }
        return sensorDeviceJpaRepository
                .findByActiveTrueAndSpaceIdInOrderBySpaceIdAscDeviceEuiAsc(spaceIds);
    }
}
