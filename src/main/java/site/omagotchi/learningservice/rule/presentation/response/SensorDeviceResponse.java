package site.omagotchi.learningservice.rule.presentation.response;

import site.omagotchi.learningservice.rule.application.result.SensorDeviceResult;

public record SensorDeviceResponse (
        String deviceEui,
        Long spaceId,                    // ← 추가
        String displayName,
        String model,
        String installationPoint,
        Integer expectedIntervalSeconds, // ← 추가
        boolean active
){
    public static SensorDeviceResponse from(SensorDeviceResult result){
        return new SensorDeviceResponse(
                result.deviceEui(),
                result.spaceId(),
                result.displayName(),
                result.model(),
                result.installationPoint(),
                result.expectedIntervalSeconds(),
                result.active()
        );
    }
}
