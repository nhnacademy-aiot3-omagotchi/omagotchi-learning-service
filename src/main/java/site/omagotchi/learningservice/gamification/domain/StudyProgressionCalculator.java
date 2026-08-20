package site.omagotchi.learningservice.gamification.domain;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class StudyProgressionCalculator {

    public static final long FOUR_HOURS_SECONDS = 14_400; // 4h(seconds)
    public static final long SIX_HOURS_SECONDS = 21_600; // 6h(seconds)
    public static final long EIGHT_HOURS_SECONDS = 28_800; // 8h(seconds)

    public static StudyProgressionState calculate(long studySeconds) {
        return new StudyProgressionState(
                studySeconds,
                studySeconds >= FOUR_HOURS_SECONDS,
                studySeconds >= SIX_HOURS_SECONDS,
                studySeconds >= EIGHT_HOURS_SECONDS
        );
    }
}
