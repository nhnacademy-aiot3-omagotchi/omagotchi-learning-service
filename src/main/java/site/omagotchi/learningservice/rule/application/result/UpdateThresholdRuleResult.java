package site.omagotchi.learningservice.rule.application.result;

public record UpdateThresholdRuleResult (
        boolean changed,
        Long ruleVersion
){
}
