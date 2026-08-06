package site.omagotchi.learningservice.gamification.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("평일 스트릭 계산")
class WeekdayStreakCalculatorTest {

    @Test
    @DisplayName("평일 3일 연속 출석이면 스트릭 달성")
    void qualifiesAfterThreeWeekdays() {
        WeekdayStreakState state = WeekdayStreakCalculator.calculate(
                LocalDate.of(2026, 8, 5),
                Set.of(
                        LocalDate.of(2026, 8, 3),
                        LocalDate.of(2026, 8, 4),
                        LocalDate.of(2026, 8, 5)
                )
        );

        assertAll(
                () -> assertEquals(3, state.currentWeekdayStreakDays()),
                () -> assertTrue(state.qualified())
        );
    }

    @Test
    @DisplayName("주말은 스트릭 단절도 연장도 하지 않는다")
    void skipsWeekend() {
        WeekdayStreakState state = WeekdayStreakCalculator.calculate(
                LocalDate.of(2026, 8, 10),
                Set.of(
                        LocalDate.of(2026, 8, 6),
                        LocalDate.of(2026, 8, 7),
                        LocalDate.of(2026, 8, 10)
                )
        );

        assertEquals(3, state.currentWeekdayStreakDays());
    }

    @Test
    @DisplayName("중간 평일 결석은 스트릭을 끊는다")
    void breaksOnMissingWeekday() {
        WeekdayStreakState state = WeekdayStreakCalculator.calculate(
                LocalDate.of(2026, 8, 5),
                Set.of(
                        LocalDate.of(2026, 8, 3),
                        LocalDate.of(2026, 8, 5)
                )
        );

        assertAll(
                () -> assertEquals(1, state.currentWeekdayStreakDays()),
                () -> assertFalse(state.qualified())
        );
    }
}
