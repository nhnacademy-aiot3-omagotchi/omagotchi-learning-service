package site.omagotchi.learningservice.sensor.application.event;

import site.omagotchi.learningservice.sensor.domain.Operator;
import site.omagotchi.learningservice.sensor.domain.ThresholdRule;

/**
 * 룰이 생성 또는 변경되었다는 사실. 서비스 내부에서만 돈다.
 * </p>
 * rule-service로 나가는 표현은 ThresholdRuleChangedMessage가 따로 맡는다.
 * 그래서 여기에 필드를 더해도 서비스 간 계약은 흔들리지 않는다.
 */
public record ThresholdRuleChangedEvent(
        Long ruleId,
        String deviceEui,
        String metric,
        Operator operator,
        Double threshold,
        Long ruleVersion
) {
    public static ThresholdRuleChangedEvent from(ThresholdRule rule){
        return new ThresholdRuleChangedEvent(
                rule.getId(),
                rule.getDeviceEui(),
                rule.getMetric(),
                rule.getOperator(),
                rule.getThreshold(),
                rule.getVersion()
        );
    }
}
