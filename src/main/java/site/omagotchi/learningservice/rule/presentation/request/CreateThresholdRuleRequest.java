package site.omagotchi.learningservice.rule.presentation.request;

import jakarta.validation.constraints.*;
import site.omagotchi.learningservice.rule.application.command.CreateThresholdRuleCommand;
import site.omagotchi.learningservice.rule.domain.Operator;

import java.util.UUID;

public record CreateThresholdRuleRequest (
        @NotBlank
        @Pattern(regexp = "\\S+", message = "공백을 포함할 수 없습니다.")
        String deviceEui,

        @NotBlank
        @Size(max = 32, message = "metric은 32자를 넘을 수 없습니다.")
        String metric,

        @NotNull 
        Operator operator,

        @NotNull
        @DecimalMin(value="-1e9")
        @DecimalMax(value="1e9")
        Double threshold
){
    public CreateThresholdRuleCommand toCommand(UUID requesterId, String requestId){
        return new CreateThresholdRuleCommand(deviceEui, metric, operator, threshold, requesterId, requestId);
    }
}