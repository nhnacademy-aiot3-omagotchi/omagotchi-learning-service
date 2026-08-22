package site.omagotchi.learningservice.sensor.domain;

/** 공간에 속한 센서 하나. point는 InfluxDB 태그, displayName은 sensor_devices 테이블에서 온다. */
public record SensorRef(
        String deviceEui,
        String point,
        String displayName
) {

    /** 표시명을 채운 새 값을 만든다. record는 값을 바꿀 수 없어 새로 만든다. */
    public SensorRef withDisplayName(String displayName) {
        return new SensorRef(deviceEui, point, displayName);
    }
}