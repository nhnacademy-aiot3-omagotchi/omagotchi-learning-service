package site.omagotchi.learningservice.sensor.domain;

import java.time.Instant;
import java.util.List;

public record SensorSeries(
        String deviceEui,
        String deviceDisplayName,
        String measurement,
        SeriesWindow window,
        Instant from,
        Instant to,
        List<SeriesPoint> points
) {
}