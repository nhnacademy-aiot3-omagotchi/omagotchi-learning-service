package site.omagotchi.learningservice.rule.application.command;

import java.time.Instant;

public record UpdateSensorDeviceCommand (
        String deviceEui,
        Long spaceId,
        String displayName,
        String installationPoint,
        Integer expectedIntervalSeconds,
        Instant installedAt
){ }
