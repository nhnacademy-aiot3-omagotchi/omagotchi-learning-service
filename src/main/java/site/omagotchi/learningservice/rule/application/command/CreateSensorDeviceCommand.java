package site.omagotchi.learningservice.rule.application.command;

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
