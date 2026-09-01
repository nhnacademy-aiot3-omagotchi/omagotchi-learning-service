package site.omagotchi.learningservice.sensor.application.command;

import site.omagotchi.learningservice.sensor.domain.Operator;

public record UpdateThresholdRuleCommand (
        Long baseVersion,
        Operator operator,
        Double threshold
){ }
