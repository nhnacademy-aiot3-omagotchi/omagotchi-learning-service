package site.omagotchi.learningservice.rule.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import site.omagotchi.learningservice.rule.application.command.CreateThresholdRuleCommand;
import site.omagotchi.learningservice.rule.domain.Operator;

import java.util.UUID;

public record CreateThresholdRuleRequest (
        @NotBlank
        @Pattern(regexp = "\\S+", message = "공백을 포함할 수 없습니다.")
        String deviceEui,

        @NotBlank String metric,
        @NotNull Operator operator,
        @NotNull Double threshold
){
    public CreateThresholdRuleCommand toCommand(UUID requesterId, String requestId){
        return new CreateThresholdRuleCommand(deviceEui, metric, operator, threshold, requesterId, requestId);
    }
}