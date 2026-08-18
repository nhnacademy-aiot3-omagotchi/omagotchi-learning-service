package site.omagotchi.learningservice.sensor.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sensor.influx")
public record SensorInfluxProperties(
        String url,
        String token,
        String org,
        Buckets buckets
) {
    public record Buckets(
            String raw,
            String avg1h,
            String avg1d
    ){

    }
}
