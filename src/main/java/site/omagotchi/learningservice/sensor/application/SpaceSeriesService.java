package site.omagotchi.learningservice.sensor.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;
import site.omagotchi.learningservice.rule.application.SensorDeviceService;
import site.omagotchi.learningservice.sensor.application.port.SpaceSeriesRepository;
import site.omagotchi.learningservice.sensor.application.query.SpaceSeriesQuery;
import site.omagotchi.learningservice.sensor.domain.SensorRef;
import site.omagotchi.learningservice.sensor.domain.SeriesWindow;
import site.omagotchi.learningservice.sensor.domain.SpaceSeries;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SpaceSeriesService {

    private final SpaceSeriesRepository spaceSeriesRepository;
    private final SensorDeviceService sensorDeviceService;
    private final SensorSeriesProperties properties;
    private final Clock clock;

    public SpaceSeries getSpaceSeries(String location, String measurement, String window) {
        SeriesWindow seriesWindow = toWindow(window);

        Instant now = clock.instant();
        Instant from = seriesWindow.from(now);
        Instant boundary = seriesWindow.settledUntil(now, properties.zone());

        // 운영 중인 기기만 집계한다. 미등록·비활성 기기는 평균에 섞이지 않는다
        Set<String> activeEuis = sensorDeviceService.findActiveDeviceEuis();

        SpaceSeries series = spaceSeriesRepository.findSpaceSeries(
                new SpaceSeriesQuery(location, measurement, seriesWindow,
                        from, boundary, now, activeEuis));

        return withDisplayNames(series);
    }

    /** 저장소는 표시명을 모른다. 기기 마스터에서 찾아 채운다. */
    private SpaceSeries withDisplayNames(SpaceSeries series) {
        List<SensorRef> named = series.sensors().stream()
                .map(sensor -> sensor.withDisplayName(
                        sensorDeviceService.findDisplayName(sensor.deviceEui()).orElse(null)))
                .toList();

        return new SpaceSeries(series.location(), series.measurement(), series.window(),
                series.from(), series.to(), named, series.points());
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