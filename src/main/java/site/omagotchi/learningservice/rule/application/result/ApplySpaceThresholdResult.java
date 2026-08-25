package site.omagotchi.learningservice.rule.application.result;

public record ApplySpaceThresholdResult (
        Long spaceId,
        int deviceCount,
        int applied,
        int unchanged,
        int missing
) { }
