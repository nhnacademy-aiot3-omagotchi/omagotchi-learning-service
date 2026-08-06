package site.omagotchi.learningservice.gamification.domain;

public record WeekdayStreakState(
        int currentWeekdayStreakDays,
        boolean qualified
) {
}
