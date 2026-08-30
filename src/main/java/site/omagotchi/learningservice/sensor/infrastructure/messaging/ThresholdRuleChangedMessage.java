package site.omagotchi.learningservice.sensor.infrastructure.messaging;

import site.omagotchi.learningservice.sensor.application.event.ThresholdRuleChangedEvent;
/**
 * RabbitMQ로 나가는 페이로드. rule-service와의 계약.
 */
public record ThresholdRuleChangedMessage(
    Long ruleId,
    String deviceEui,
    String metric,
    String operator,
    Double threshold,
    Long ruleVersion
){
    public static ThresholdRuleChangedMessage from(ThresholdRuleChangedEvent event) {
        return new ThresholdRuleChangedMessage(
                event.ruleId(),
                event.deviceEui(),
                event.metric(),
                event.operator().name(),
                event.threshold(),
                event.ruleVersion()
        );
    }
}
