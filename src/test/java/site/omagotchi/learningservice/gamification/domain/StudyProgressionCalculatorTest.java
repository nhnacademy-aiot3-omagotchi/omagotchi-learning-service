package site.omagotchi.learningservice.gamification.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("학습 progression 계산")
class StudyProgressionCalculatorTest {

    @Test
    @DisplayName("4h, 6h, 8h 기준 달성 여부를 계산한다")
    void calculatesStudyHourThresholds() {
        StudyProgressionState fourHours = StudyProgressionCalculator.calculate(14_400);
        StudyProgressionState sixHours = StudyProgressionCalculator.calculate(21_600);
        StudyProgressionState eightHours = StudyProgressionCalculator.calculate(28_800);

        assertAll(
                () -> assertTrue(fourHours.reachedFourHours()),
                () -> assertFalse(fourHours.reachedSixHours()),
                () -> assertTrue(sixHours.reachedSixHours()),
                () -> assertFalse(sixHours.reachedEightHours()),
                () -> assertTrue(eightHours.reachedEightHours())
        );
    }
}
