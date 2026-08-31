package site.omagotchi.learningservice.sensor.application.port;

import site.omagotchi.learningservice.sensor.domain.ThresholdRule;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ThresholdRuleRepository {

    ThresholdRule save(ThresholdRule rule);

    void update(ThresholdRule rule);

    boolean existsByDeviceEuiAndMetric(String deviceEui, String metric);

    Optional<ThresholdRule> findById(Long ruleId);

    /**
     * Rule Engine 적재용. 기수를 가리지 않되 <b>회수된 기기(active=false)의 룰은 제외한다.</b>
     *
     * <p>중립적인 {@code findAll}을 두지 않는 이유다 — 전수 조회가 필요한 곳은 이 경로
     * 하나뿐이고, 이름이 중립적이면 기수 범위를 지켜야 할 사용자 API가 실수로 부른다.</p>
     */
    List<ThresholdRule> findAllWithActiveDevice();

    List<ThresholdRule> findByDeviceEuiIn(Collection<String> deviceEui);

    Optional<ThresholdRule> findByDeviceEuiAndMetric(String deviceEui, String metric);
}
