package site.omagotchi.learningservice.rule.presentation.request;

import jakarta.validation.constraints.NotNull;

public record UpdateSensorActiveRequest (
        @NotNull Boolean active
){
}
