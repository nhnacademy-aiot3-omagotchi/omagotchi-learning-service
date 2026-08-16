package site.omagotchi.learningservice.rule.application.result;

import site.omagotchi.learningservice.rule.domain.SensorDevice;

public record SensorDeviceResult (
        String deviceEui,
        String displayName,
        String model,
        String installationPoint,
        boolean active
){
    public static SensorDeviceResult from(SensorDevice device){
        return new SensorDeviceResult(
                device.getDeviceEui(),
                device.getDisplayName(),
                device.getModel(),
                device.getInstallationPoint(),
                device.getActive()
        );
    }
}
