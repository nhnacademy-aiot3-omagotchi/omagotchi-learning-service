package site.omagotchi.learningservice.rule.presentation.request;

import site.omagotchi.learningservice.rule.application.command.CreateSensorDeviceCommand;

import java.time.Instant;

public record CreateSensorDeviceRequest(
        String deviceEui,
        Long spaceId,
        String model,
        String displayName,
        String installationPoint,
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
