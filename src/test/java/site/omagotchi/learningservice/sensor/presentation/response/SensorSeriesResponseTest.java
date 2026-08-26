package site.omagotchi.learningservice.sensor.presentation.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.omagotchi.learningservice.sensor.application.result.SensorSeries;
import site.omagotchi.learningservice.sensor.domain.SeriesPoint;
import site.omagotchi.learningservice.sensor.domain.SeriesWindow;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("센서 시계열 응답")
class SensorSeriesResponseTest {

    private static final Instant FROM = Instant.parse("2026-08-24T10:30:00Z");
    private static final Instant TO = Instant.parse("2026-08-25T10:30:00Z");

    @Test
    @DisplayName("결과 필드가 응답으로 그대로 옮겨지고 부가 정보가 채워진다")
    void mapsFieldsAndDerivedInfo() {
        // given
        SensorSeries series = new SensorSeries(
                "0011223344556677", "강의실 온도계", "temperature",
                SeriesWindow.DAY, FROM, TO, List.of());

        // when
        SensorSeriesResponse response = SensorSeriesResponse.from(series);

        // then
        assertEquals("0011223344556677", response.deviceEui());
        assertEquals("강의실 온도계", response.deviceDisplayName());
        assertEquals("temperature", response.measurement());
        assertEquals(SeriesWindow.DAY, response.window());
        assertEquals("1h", response.interval());
        assertEquals(FROM, response.from());
        assertEquals(TO, response.to());
        assertEquals(TO, response.serverTime());
        assertEquals("AVG_1H", response.sources().settled());
        assertEquals("RAW", response.sources().hot());
    }

    @Test
    @DisplayName("확정된 점의 partial은 null이고 진행 중인 점만 TRUE가 된다")
    void marksOnlyHotPointsAsPartial() {
        // given: 확정 점 하나, 진행 중 점 하나
        List<SeriesPoint> points = List.of(
                new SeriesPoint(Instant.parse("2026-08-25T09:00:00Z"), 23.5, false),
                new SeriesPoint(Instant.parse("2026-08-25T10:00:00Z"), 24.0, true));
        SensorSeries series = new SensorSeries(
                "0011223344556677", null, "temperature",
                SeriesWindow.DAY, FROM, TO, points);

        // when
        SensorSeriesResponse response = SensorSeriesResponse.from(series);

        // then
        assertEquals(2, response.points().size());
        assertEquals(23.5, response.points().get(0).value());
        assertNull(response.points().get(0).partial());          // 확정 → 필드 없음
        assertEquals(Boolean.TRUE, response.points().get(1).partial()); // 진행 중 → TRUE
    }
}