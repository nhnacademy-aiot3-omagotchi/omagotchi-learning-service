package site.omagotchi.learningservice.sensor.application.command;

import java.time.Instant;

public record CreateSensorDeviceCommand(
        String deviceEui,
        Long spaceId,
        String model,
        String displayName,
        String installationPoint,
        Integer expectedIntervalSeconds,
        Instant installedAt
){ }
