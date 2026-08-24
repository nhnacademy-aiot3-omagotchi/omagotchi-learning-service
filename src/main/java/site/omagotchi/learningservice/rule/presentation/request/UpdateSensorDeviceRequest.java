package site.omagotchi.learningservice.rule.presentation.request;

import site.omagotchi.learningservice.rule.application.command.UpdateSensorDeviceCommand;

import java.time.Instant;

public record UpdateSensorDeviceRequest(
        Long spaceId,
        String displayName,
        String installedPoint,
        Integer expectedIntervalSeconds,
        Instant installedAt
) {

    public UpdateSensorDeviceCommand toCommand(String deviceEui){
        return new UpdateSensorDeviceCommand(
                deviceEui,
                spaceId,
                displayName,
                installedPoint,
                expectedIntervalSeconds,
                installedAt
        );
    }
}
