package site.omagotchi.learningservice.sensor.application.result;

import site.omagotchi.learningservice.sensor.domain.SeriesWindow;
import site.omagotchi.learningservice.sensor.domain.SpaceSeriesPoint;

import java.time.Instant;
import java.util.List;

public record SpaceSeries(
        String location,
        String measurement,
        SeriesWindow window,
        Instant from,
        Instant to,
        List<SensorRef> sensors,
        List<SpaceSeriesPoint> points
) {
}