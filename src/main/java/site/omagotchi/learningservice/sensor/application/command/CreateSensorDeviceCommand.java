package site.omagotchi.learningservice.sensor.application.command;

import java.time.Instant;

public record CreateSensorDeviceCommand(
        Long spaceId,
        String deviceEui,
        String model,
        String displayName,
        String installationPoint,
        Integer expectedIntervalSeconds,
        Instant installedAt
){ }
