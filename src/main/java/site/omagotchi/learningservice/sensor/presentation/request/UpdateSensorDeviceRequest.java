package site.omagotchi.learningservice.sensor.presentation.request;

import jakarta.validation.constraints.NotNull;
import site.omagotchi.learningservice.sensor.application.command.UpdateSensorDeviceCommand;

import java.time.Instant;

public record UpdateSensorDeviceRequest(
        @NotNull
        Long spaceId,
        String displayName,
        String installationPoint,
        Integer expectedIntervalSeconds,
        Instant installedAt
) {

    public UpdateSensorDeviceCommand toCommand() {
        return new UpdateSensorDeviceCommand(
                spaceId,
                displayName,
                installationPoint,
                expectedIntervalSeconds,
                installedAt
        );
    }
}
