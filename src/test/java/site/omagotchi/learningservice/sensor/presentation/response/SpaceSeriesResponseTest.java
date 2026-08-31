package site.omagotchi.learningservice.sensor.presentation.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.omagotchi.learningservice.sensor.application.result.SensorRef;
import site.omagotchi.learningservice.sensor.application.result.SpaceSeries;
import site.omagotchi.learningservice.sensor.domain.SeriesWindow;
import site.omagotchi.learningservice.sensor.domain.SpaceSeriesPoint;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("공간 시계열 응답")
class SpaceSeriesResponseTest {

    private static final Instant FROM = Instant.parse("2026-07-26T10:30:00Z");
    private static final Instant TO = Instant.parse("2026-08-25T10:30:00Z");

    @Test
    @DisplayName("센서 명단과 센서 수, 집계 점이 응답으로 옮겨진다")
    void mapsSensorsAndPoints() {
        // given
        List<SensorRef> sensors = List.of(
                new SensorRef("0011223344556677", "window-side", "창가 CO2 센서"),
                new SensorRef("8899aabbccddeeff", "door-side", null));
        List<SpaceSeriesPoint> points = List.of(
                new SpaceSeriesPoint(Instant.parse("2026-08-25T09:00:00Z"),
                        450.0, 400.0, "0011223344556677",
                        500.0, "8899aabbccddeeff", 2, false),
                SpaceSeriesPoint.empty(Instant.parse("2026-08-25T10:00:00Z"), true));
        SpaceSeries series = new SpaceSeries("A강의실", "co2", SeriesWindow.MONTH,
                FROM, TO, sensors, points);

        // when
        SpaceSeriesResponse response = SpaceSeriesResponse.from(series);

        // then
        assertEquals("A강의실", response.location());
        assertEquals("1d", response.interval());
        assertEquals("AVG_1D", response.sources().settled());
        assertEquals("AVG_1H", response.sources().hot());

        assertEquals(2, response.sensorCount());
        assertEquals("창가 CO2 센서", response.sensors().get(0).displayName());
        assertNull(response.sensors().get(1).displayName());

        assertEquals(450.0, response.points().get(0).avg());
        assertEquals("0011223344556677", response.points().get(0).minDeviceEui());
        assertNull(response.points().get(0).partial());
        assertEquals(Boolean.TRUE, response.points().get(1).partial());
    }
}