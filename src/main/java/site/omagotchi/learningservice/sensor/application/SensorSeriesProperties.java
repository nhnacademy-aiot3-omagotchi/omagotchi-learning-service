package site.omagotchi.learningservice.sensor.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

@ConfigurationProperties(prefix = "sensor.series")
public record SensorSeriesProperties(ZoneId zone) {

    public SensorSeriesProperties {
        zone = zone == null ? ZoneId.of("Asia/Seoul") : zone;
    }
}