package site.omagotchi.learningservice.sensor.application.result;

public record ApplySpaceThresholdResult (
        Long spaceId,
        int deviceCount,
        int applied,
        int unchanged,
        int missing
) { }
