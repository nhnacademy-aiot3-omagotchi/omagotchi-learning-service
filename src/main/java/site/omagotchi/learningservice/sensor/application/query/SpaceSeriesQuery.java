package site.omagotchi.learningservice.sensor.application.query;

import site.omagotchi.learningservice.sensor.domain.SeriesWindow;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;

/** 공간 단위 조회 조건 */
public record SpaceSeriesQuery(
        String location,
        String measurement,
        SeriesWindow window,
        Instant from,
        Instant boundary,
        Instant to,
        ZoneId zone,
        Set<String> includedDeviceEuis
) {
}