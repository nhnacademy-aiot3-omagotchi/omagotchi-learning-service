package site.omagotchi.learningservice.sensor.presentation.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import site.omagotchi.learningservice.sensor.application.command.UpdateThresholdRuleCommand;
import site.omagotchi.learningservice.sensor.domain.Operator;

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
    public UpdateThresholdRuleCommand toCommand() {
        return new UpdateThresholdRuleCommand(baseVersion, operator, threshold);
    }
}
