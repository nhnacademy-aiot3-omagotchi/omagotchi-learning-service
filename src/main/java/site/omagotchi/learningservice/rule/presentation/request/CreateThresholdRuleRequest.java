package site.omagotchi.learningservice.rule.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import site.omagotchi.learningservice.rule.application.command.CreateThresholdRuleCommand;
import site.omagotchi.learningservice.rule.domain.Operator;

import java.util.UUID;

public record CreateThresholdRuleRequest (
        @NotBlank String deviceEui,
        @NotBlank String metric,
        @NotNull Operator operator,
        @NotNull Double threshold
){
    public CreateThresholdRuleCommand toCommand(UUID requesterId, String requestId){
        return new CreateThresholdRuleCommand(deviceEui, metric, operator, threshold, requesterId, requestId);
    }
}