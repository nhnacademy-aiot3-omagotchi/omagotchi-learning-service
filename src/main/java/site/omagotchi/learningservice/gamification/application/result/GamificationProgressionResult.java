package site.omagotchi.learningservice.gamification.application.result;

import site.omagotchi.learningservice.gamification.domain.StudyProgressionState;
import site.omagotchi.learningservice.gamification.domain.WeekdayStreakState;

import java.time.LocalDate;

public record GamificationProgressionResult(
        LocalDate aggregationDate,
        StudyProgressionState studyProgression,
        WeekdayStreakState weekdayStreak
) {
}
