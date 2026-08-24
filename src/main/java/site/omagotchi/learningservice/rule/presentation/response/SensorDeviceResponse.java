package site.omagotchi.learningservice.rule.presentation.response;

import site.omagotchi.learningservice.rule.application.result.SensorDeviceResult;

public record SensorDeviceResponse (
        String deviceEui,
        String displayName,
        String model,
        String installationPoint,
        boolean active
){
    public static SensorDeviceResponse from(SensorDeviceResult result){
        return new SensorDeviceResponse(
                result.deviceEui(),
                result.displayName(),
                result.model(),
                result.installationPoint(),
                result.active()
        );
    }
}
