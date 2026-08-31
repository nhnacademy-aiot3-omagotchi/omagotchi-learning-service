package site.omagotchi.learningservice.sensor.application.result;

import site.omagotchi.learningservice.sensor.domain.SensorDevice;

public record SensorDeviceResult (
        String deviceEui,
        Long spaceId,
        String displayName,
        String model,
        String installationPoint,
        Integer expectedIntervalSeconds,
        boolean active
){
    public static SensorDeviceResult from(SensorDevice device){
        return new SensorDeviceResult(
                device.getDeviceEui(),
                device.getSpaceId(),
                device.getDisplayName(),
                device.getModel(),
                device.getInstallationPoint(),
                device.getExpectedIntervalSeconds(),
                device.getActive()
        );
    }
}
