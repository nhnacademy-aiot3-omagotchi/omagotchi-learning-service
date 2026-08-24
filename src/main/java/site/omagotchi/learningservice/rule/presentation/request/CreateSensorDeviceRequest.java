package site.omagotchi.learningservice.rule.presentation.request;

import jakarta.validation.constraints.*;
import site.omagotchi.learningservice.rule.application.command.CreateSensorDeviceCommand;

import java.time.Instant;

public record CreateSensorDeviceRequest(
        @NotBlank
        @Pattern(
                regexp = "[0-9a-f]+",
                message = "장치 EUI는 16진수여야 합니다."
        )
        @Size(max = 32)
        String deviceEui,

        Long spaceId,

        @NotBlank
        @Size(max = 32)
        String model,

        @NotBlank
        @Size(max = 64)
        String displayName,

        @Size(max = 64)
        String installationPoint,

        @NotNull
        @Positive
        Integer expectedIntervalSeconds,

        Instant installedAt
) {

    public CreateSensorDeviceCommand toCommand(){
        return new CreateSensorDeviceCommand(
                deviceEui,
                spaceId,
                model,
                displayName,
                installationPoint,
                expectedIntervalSeconds,
                installedAt
        );
    }
}
