package site.omagotchi.learningservice.sensor.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.learningservice.sensor.domain.SensorDevice;

import java.util.List;

public interface SensorDeviceJpaRepository extends JpaRepository<SensorDevice, String> {

    List<SensorDevice> findBySpaceId(Long spaceId);

    List<SensorDevice> findBySpaceIdIsNotNullOrderBySpaceIdAsc();

    List<SensorDevice> findByActiveTrue();
}
