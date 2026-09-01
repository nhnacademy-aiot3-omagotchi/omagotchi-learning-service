package site.omagotchi.learningservice.sensor.application.command;

import site.omagotchi.learningservice.sensor.domain.Operator;

public record CreateThresholdRuleCommand (
        String deviceEui,
        String metric,
        Operator operator,
        Double threshold
){ }
