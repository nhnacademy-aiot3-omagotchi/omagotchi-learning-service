package site.omagotchi.learningservice.gamification.presentation.response;

import site.omagotchi.learningservice.gamification.application.result.GamificationProgressionResult;

import java.time.LocalDate;

public record GamificationProgressionResponse(
        LocalDate aggregationDate,
        long studySeconds,
        boolean reachedFourHours,
        boolean reachedSixHours,
        boolean reachedEightHours,
        int currentWeekdayStreakDays,
        boolean streakQualified
) {

    public static GamificationProgressionResponse from(GamificationProgressionResult result) {
        return new GamificationProgressionResponse(
                result.aggregationDate(),
                result.studyProgression().studySeconds(),
                result.studyProgression().reachedFourHours(),
                result.studyProgression().reachedSixHours(),
                result.studyProgression().reachedEightHours(),
                result.weekdayStreak().currentWeekdayStreakDays(),
                result.weekdayStreak().qualified()
        );
    }
}
