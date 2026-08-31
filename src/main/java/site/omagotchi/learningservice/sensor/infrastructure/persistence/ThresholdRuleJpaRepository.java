package site.omagotchi.learningservice.sensor.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import site.omagotchi.learningservice.sensor.domain.ThresholdRule;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ThresholdRuleJpaRepository extends JpaRepository<ThresholdRule, Long> {
    boolean existsByDeviceEuiAndMetric(String deviceEui, String metric);

    Optional<ThresholdRule> findByDeviceEuiAndMetric(String deviceEui, String metric);
    
    List<ThresholdRule> findByDeviceEuiInOrderByDeviceEuiAsc(Collection<String> deviceEuis);

    /**
     * 활성 기기의 룰만 읽는다.
     *
     * <p>device_eui로 조인한다 — ThresholdRule은 SensorDevice를 연관으로 들지 않고 자연키만
     * 가진다. 참조 정합성은 스키마의 FK가 지키므로 여기서는 필터링만 한다.</p>
     */
    @Query("""
            SELECT rule
              FROM ThresholdRule rule
              JOIN SensorDevice device ON device.deviceEui = rule.deviceEui
             WHERE device.active = true
             ORDER BY rule.deviceEui
            """)
    List<ThresholdRule> findAllWithActiveDevice();
}
