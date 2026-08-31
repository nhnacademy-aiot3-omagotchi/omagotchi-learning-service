package site.omagotchi.learningservice.sensor.presentation.response;

import site.omagotchi.learningservice.sensor.domain.Operator;
import site.omagotchi.learningservice.sensor.domain.ThresholdRule;

public record ThresholdRuleResponse (
        Long ruleId,
        String deviceEui,
        String metric,
        Operator operator,
        Double threshold,
        Long ruleVersion
){
    public static ThresholdRuleResponse from(ThresholdRule rule){
        return new ThresholdRuleResponse(
                rule.getId(),
                rule.getDeviceEui(),
                rule.getMetric(),
                rule.getOperator(),
                rule.getThreshold(),
                rule.getVersion()
        );
    }
}
