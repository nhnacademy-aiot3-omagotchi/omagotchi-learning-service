package site.omagotchi.learningservice.sensor.application.command;

import site.omagotchi.learningservice.sensor.domain.Operator;

import java.util.UUID;

public record UpdateThresholdRuleCommand (
        Long ruleId,
        Long baseVersion,
        Operator operator,
        Double threshold,
        UUID requesterId,
        String requestId
){ }
