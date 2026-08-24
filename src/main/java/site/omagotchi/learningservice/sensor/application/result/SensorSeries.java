package site.omagotchi.learningservice.sensor.application.result;

import site.omagotchi.learningservice.sensor.domain.SeriesPoint;
import site.omagotchi.learningservice.sensor.domain.SeriesWindow;

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