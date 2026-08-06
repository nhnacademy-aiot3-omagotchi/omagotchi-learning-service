package site.omagotchi.learningservice.rule.application.command;

import site.omagotchi.learningservice.rule.domain.Operator;

import java.util.UUID;

public record CreateThresholdRuleCommand (
        String deviceEui,
        String metric,
        Operator operator,
        Double threshold,
        UUID requesterId,
        String requestId
){ }
