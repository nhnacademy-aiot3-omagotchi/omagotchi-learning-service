package site.omagotchi.learningservice.sensor.application.result;

import site.omagotchi.learningservice.sensor.domain.Operator;
import site.omagotchi.learningservice.sensor.domain.ThresholdRule;

public record ThresholdConditionResult(
    Operator operator,
    double threshold
){
    public static ThresholdConditionResult from(ThresholdRule rule){
        return new ThresholdConditionResult(rule.getOperator(), rule.getThreshold());
    }
}
