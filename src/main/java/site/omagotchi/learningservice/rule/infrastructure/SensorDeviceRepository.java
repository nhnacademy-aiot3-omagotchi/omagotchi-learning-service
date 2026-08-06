package site.omagotchi.learningservice.rule.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.learningservice.rule.domain.SensorDevice;

public interface SensorDeviceRepository extends JpaRepository<SensorDevice, String> {
}
