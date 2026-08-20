package site.omagotchi.learningservice.sensor.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.omagotchi.learningservice.rule.application.port.SensorDeviceRepository;
import site.omagotchi.learningservice.sensor.application.port.SensorSeriesRepository;
import site.omagotchi.learningservice.sensor.application.query.SensorSeriesQuery;
import site.omagotchi.learningservice.sensor.domain.SensorSeries;
import site.omagotchi.learningservice.sensor.domain.SeriesPoint;
import site.omagotchi.learningservice.sensor.domain.SeriesWindow;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SensorSeriesService {

    private final SensorSeriesRepository seriesRepository;
    private final SensorDeviceRepository deviceRepository;
    private final SensorSeriesProperties properties;
    private final Clock clock;

    public SensorSeries getSeries(String deviceEui, String measurement, SeriesWindow window) {
        Instant now = clock.instant();

        Instant from = window.from(now);
        Instant boundary = window.settledUntil(now, properties.zone());

        List<SeriesPoint> points = seriesRepository.findSeries(
                new SensorSeriesQuery(deviceEui, measurement, window, from, boundary, now));

        String displayName = deviceRepository.findByDeviceEui(deviceEui)
                .map(device -> device.getDisplayName())
                .orElse(null);

        return new SensorSeries(deviceEui, displayName, measurement, window, from, now, points);
    }
}