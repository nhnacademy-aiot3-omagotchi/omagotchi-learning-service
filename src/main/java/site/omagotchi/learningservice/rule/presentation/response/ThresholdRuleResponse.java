package site.omagotchi.learningservice.rule.presentation.response;

import site.omagotchi.learningservice.rule.domain.Operator;
import site.omagotchi.learningservice.rule.domain.ThresholdRule;

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
