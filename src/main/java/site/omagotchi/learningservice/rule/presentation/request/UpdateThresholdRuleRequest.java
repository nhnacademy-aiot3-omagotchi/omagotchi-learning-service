package site.omagotchi.learningservice.rule.presentation.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import site.omagotchi.learningservice.rule.application.command.UpdateThresholdRuleCommand;
import site.omagotchi.learningservice.rule.domain.Operator;

import java.util.UUID;

public record UpdateThresholdRuleRequest(

        @NotNull
        Long baseVersion,

        @NotNull
        Operator operator,

        @NotNull
        @DecimalMin(value="-1e9")
        @DecimalMax(value="1e9")
        Double threshold
){
    public UpdateThresholdRuleCommand toCommand(Long ruleId, UUID requesterId, String requestId){
        return new UpdateThresholdRuleCommand(ruleId, baseVersion, operator, threshold, requesterId, requestId);
    }
}
