package site.omagotchi.learningservice.sensor.application.port;

import site.omagotchi.learningservice.sensor.domain.ThresholdRule;

/**
 * 룰 변경 사실을 알리는 발행 경계.
 * </p>
 * 호출 시점은 상태 변경과 같은 트랜잭션 안이지만 실제 발송은 커밋 후다 —
 * 그 시점은 리스너의 AFTER_COMMIT이 정한다. 여기서 커밋을 기다리지 않는다.
 */
public interface ThresholdRuleEventPublisher {
    void publishThresholdRuleChanged(ThresholdRule rule);
}
