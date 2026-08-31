package site.omagotchi.learningservice.sensor.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("조회 창")
class SeriesWindowTest {

    private static final Instant NOW = Instant.parse("2026-08-25T10:30:00Z");
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Test
    @DisplayName("DAY의 조회 시작 시각은 1일 전이다")
    void dayStartsOneDayAgo() {
        Instant from = SeriesWindow.DAY.from(NOW);

        assertEquals(Instant.parse("2026-08-24T10:30:00Z"), from);
    }

    @Test
    @DisplayName("WEEK의 조회 시작 시각은 7일 전이다")
    void weekStartsSevenDaysAgo() {
        Instant from = SeriesWindow.WEEK.from(NOW);

        assertEquals(Instant.parse("2026-08-18T10:30:00Z"), from);
    }

    @Test
    @DisplayName("MONTH의 조회 시작 시각은 30일 전이다")
    void monthStartsThirtyDaysAgo() {
        Instant from = SeriesWindow.MONTH.from(NOW);

        assertEquals(Instant.parse("2026-07-26T10:30:00Z"), from);
    }

    @Test
    @DisplayName("DAY의 확정 경계는 현재 시각을 시간 단위로 내림한 지점이다")
    void daySettlesToHourFloor() {
        Instant boundary = SeriesWindow.DAY.settledUntil(NOW, SEOUL);

        assertEquals(Instant.parse("2026-08-25T10:00:00Z"), boundary);
    }

    @Test
    @DisplayName("MONTH의 확정 경계는 서울 기준 그날 자정이다")
    void monthSettlesToSeoulMidnight() {
        Instant boundary = SeriesWindow.MONTH.settledUntil(NOW, SEOUL);

        assertEquals(Instant.parse("2026-08-24T15:00:00Z"), boundary);
    }

    @Test
    @DisplayName("시간 단위 창은 1h, 일 단위 창은 1d를 Flux 간격으로 준다")
    void fluxIntervalMatchesUnit() {
        assertEquals("1h", SeriesWindow.DAY.fluxInterval());
        assertEquals("1h", SeriesWindow.WEEK.fluxInterval());
        assertEquals("1d", SeriesWindow.MONTH.fluxInterval());
    }
}