package site.omagotchi.learningservice.gamification.domain;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class WeekdayStreakCalculator {

    public static WeekdayStreakState calculate(LocalDate baseDate, Set<LocalDate> attendedDates) {
        int currentStreakDays = 0;
        LocalDate cursor = baseDate;

        while (true) {
            if (isWeekend(cursor)) {
                cursor = cursor.minusDays(1);
                continue;
            }
            if (!attendedDates.contains(cursor)) {
                break;
            }
            currentStreakDays++;
            cursor = cursor.minusDays(1);
        }

        return new WeekdayStreakState(currentStreakDays, currentStreakDays >= 3);
    }

    public static boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }
}
