package site.omagotchi.learningservice.sensor.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.omagotchi.learningservice.sensor.domain.SensorDevice;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SensorDeviceJpaRepository extends JpaRepository<SensorDevice, String> {

    List<SensorDevice> findBySpaceIdInOrderBySpaceIdAscDeviceEuiAsc(Collection<Long> spaceIds);

    List<SensorDevice> findByActiveTrueAndSpaceIdInOrderBySpaceIdAscDeviceEuiAsc(Collection<Long> spaceIds);

    long countBySpaceId(Long spaceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT device FROM SensorDevice device WHERE device.deviceEui = :deviceEui")
    Optional<SensorDevice> findByDeviceEuiForUpdate(@Param("deviceEui") String deviceEui);
}
