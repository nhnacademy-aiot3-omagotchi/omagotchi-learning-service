package site.omagotchi.learningservice.sensor.presentation.response;

public record UpdateThresholdRuleResponse (
        boolean changed,
        Long ruleVersion
){ }
