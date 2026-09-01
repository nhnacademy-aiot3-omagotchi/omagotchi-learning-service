package site.omagotchi.learningservice.sensor.application.command;

import java.time.Instant;

public record UpdateSensorDeviceCommand (
        Long spaceId,
        String displayName,
        String installationPoint,
        Integer expectedIntervalSeconds,
        Instant installedAt
){ }
