package site.omagotchi.learningservice.rule.application.command;

import site.omagotchi.learningservice.rule.domain.Operator;

import java.util.UUID;

public record UpdateThresholdRuleCommand (
        Long ruleId,
        Long baseVersion,
        Operator operator,
        Double threshold,
        UUID requesterId,
        String requestId
){ }
