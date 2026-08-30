package site.omagotchi.learningservice.sensor.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;
import site.omagotchi.learningservice.sensor.application.port.SensorSeriesRepository;
import site.omagotchi.learningservice.sensor.application.query.SensorSeriesQuery;
import site.omagotchi.learningservice.sensor.application.result.SensorSeries;
import site.omagotchi.learningservice.sensor.domain.SeriesPoint;
import site.omagotchi.learningservice.sensor.domain.SeriesWindow;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SensorSeriesService {

    private final SensorSeriesRepository seriesRepository;
    private final SensorDeviceService sensorDeviceService;
    private final SensorSeriesProperties properties;
    private final Clock clock;

    public SensorSeries getSeries(String deviceEui, String measurement, String window) {
        SeriesWindow seriesWindow = toWindow(window);

        Instant now = clock.instant();

        Instant from = seriesWindow.from(now);
        Instant boundary = seriesWindow.settledUntil(now, properties.zone());

        List<SeriesPoint> points = seriesRepository.findSeries(
                new SensorSeriesQuery(deviceEui, measurement, seriesWindow, from, boundary, now, properties.zone()));

        String displayName = sensorDeviceService.findDisplayName(deviceEui)
                .orElse(null);

        return new SensorSeries(deviceEui, displayName, measurement, seriesWindow, from, now, points);
    }

    /** 요청 문자열을 조회 창으로 바꾼다. DAY·WEEK·MONTH가 아니면 400. */
    private SeriesWindow toWindow(String window) {
        try {
            return SeriesWindow.valueOf(window.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST, exception);
        }
    }

}