package site.omagotchi.learningservice.rule.presentation.response;

public record UpdateThresholdRuleResponse (
        boolean changed,
        Long ruleVersion
){ }
