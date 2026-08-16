package site.omagotchi.learningservice.rule.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.learningservice.rule.domain.SensorDevice;

import java.util.Collection;
import java.util.List;

public interface SensorDeviceJpaRepository extends JpaRepository<SensorDevice, String> {

    List<SensorDevice> findByDeviceEuiIn(Collection<String> deviceEuis);
}
