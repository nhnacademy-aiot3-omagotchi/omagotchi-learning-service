package site.omagotchi.learningservice.sensor.presentation.request;

import jakarta.validation.constraints.NotNull;

public record UpdateSensorActiveRequest (
        @NotNull Boolean active
){
}
