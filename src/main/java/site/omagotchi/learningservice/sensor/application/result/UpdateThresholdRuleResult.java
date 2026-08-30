package site.omagotchi.learningservice.sensor.application.result;

public record UpdateThresholdRuleResult (
        boolean changed,
        Long ruleVersion
){
}
