package site.omagotchi.learningservice.rule.application.result;

import site.omagotchi.learningservice.rule.domain.Operator;
import site.omagotchi.learningservice.rule.domain.ThresholdRule;

public record ThresholdConditionResult(
    Operator operator,
    double threshold
){
    public static ThresholdConditionResult from(ThresholdRule rule){
        return new ThresholdConditionResult(rule.getOperator(), rule.getThreshold());
    }
}
