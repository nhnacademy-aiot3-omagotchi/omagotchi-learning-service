package site.omagotchi.learningservice.sensor.application.query;

import site.omagotchi.learningservice.sensor.domain.SeriesWindow;

import java.time.Instant;
import java.time.ZoneId;

/** 조회 조건을 한 덩어리로 묶는 그릇 */
public record SensorSeriesQuery(
        String deviceEui,
        String measurement,
        SeriesWindow window,
        Instant from,
        Instant boundary,
        Instant to,
        ZoneId zone
) {
}
