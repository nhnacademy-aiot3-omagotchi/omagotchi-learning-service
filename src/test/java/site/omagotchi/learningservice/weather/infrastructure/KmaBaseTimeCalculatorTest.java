package site.omagotchi.learningservice.weather.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class KmaBaseTimeCalculatorTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 25);

    @Test
    @DisplayName("발표 10분 후 정각이면 그 발표시각을 그대로 쓴다")
    void exactlyAtAvailableMoment() {
        BaseTime result = KmaBaseTimeCalculator.calculate(LocalDateTime.of(DATE, LocalTime.of(2, 10)));

        assertThat(result.baseDate()).isEqualTo(DATE);
        assertThat(result.baseTime()).isEqualTo("0200");
    }

    @Test
    @DisplayName("발표 10분 후 1초 전이면 아직 그 발표시각을 못 쓴다 (전날 23시로 넘어감)")
    void oneSecondBeforeAvailableMoment() {
        BaseTime result = KmaBaseTimeCalculator.calculate(LocalDateTime.of(DATE, LocalTime.of(2, 9, 59)));

        assertThat(result.baseDate()).isEqualTo(DATE.minusDays(1));
        assertThat(result.baseTime()).isEqualTo("2300");
    }

    @Test
    @DisplayName("낮 12시면 11시 발표(11:10 이후 조회 가능)를 쓴다")
    void middayUsesElevenBase() {
        BaseTime result = KmaBaseTimeCalculator.calculate(LocalDateTime.of(DATE, java.time.LocalTime.of(12, 0)));

        assertThat(result.baseDate()).isEqualTo(DATE);
        assertThat(result.baseTime()).isEqualTo("1100");
    }

    @Test
    @DisplayName("14시 발표 10분 전(13:59)까지는 아직 11시 발표를 쓴다")
    void justBeforeNextBaseStillUsesPrevious() {
        BaseTime result = KmaBaseTimeCalculator.calculate(LocalDateTime.of(DATE, java.time.LocalTime.of(13, 59)));

        assertThat(result.baseTime()).isEqualTo("1100");
    }

    @Test
    @DisplayName("14시 발표 10분 후(14:10)면 14시 발표로 넘어간다")
    void switchesToNextBaseAtAvailableMoment() {
        BaseTime result = KmaBaseTimeCalculator.calculate(LocalDateTime.of(DATE, java.time.LocalTime.of(14, 10)));

        assertThat(result.baseTime()).isEqualTo("1400");
    }

    @Test
    @DisplayName("자정 직후(00:00)면 당일 발표가 하나도 없어 전날 23시 발표를 쓴다")
    void justAfterMidnightUsesPreviousDayLastBase() {
        BaseTime result = KmaBaseTimeCalculator.calculate(LocalDateTime.of(DATE, java.time.LocalTime.of(0, 0)));

        assertThat(result.baseDate()).isEqualTo(DATE.minusDays(1));
        assertThat(result.baseTime()).isEqualTo("2300");
    }

    @Test
    @DisplayName("23시 발표 10분 후(23:15)면 당일 23시 발표를 쓴다")
    void lateNightUsesSameDayLastBase() {
        BaseTime result = KmaBaseTimeCalculator.calculate(LocalDateTime.of(DATE, java.time.LocalTime.of(23, 15)));

        assertThat(result.baseDate()).isEqualTo(DATE);
        assertThat(result.baseTime()).isEqualTo("2300");
    }
}
