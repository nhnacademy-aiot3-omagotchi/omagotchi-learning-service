package site.omagotchi.learningservice.gamification.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("학습 시간 퀘스트 정책 설정")
class StudyTimeQuestPropertiesTest {

    @Test
    @DisplayName("값이 비어 있으면 기동에 실패한다")
    void rejectsMissingValue() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new StudyTimeQuestProperties(null, 41_400)
        );

        assertEquals(
                "gamification.study-time-quest.min-target-seconds은 양수여야 합니다.",
                exception.getMessage()
        );
    }

    @Test
    @DisplayName("값이 0 이하면 기동에 실패한다")
    void rejectsNonPositiveValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new StudyTimeQuestProperties(0, 41_400));
        assertThrows(IllegalArgumentException.class,
                () -> new StudyTimeQuestProperties(12_600, -1));
    }

    @Test
    @DisplayName("하한이 상한보다 크면 기동에 실패한다")
    void rejectsInvertedBounds() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new StudyTimeQuestProperties(41_400, 12_600)
        );

        assertEquals(
                "gamification.study-time-quest.min-target-seconds는 max-target-seconds보다 클 수 없습니다.",
                exception.getMessage()
        );
    }

    @Test
    @DisplayName("결정된 정책값은 그대로 통과한다")
    void acceptsDecidedPolicyValues() {
        StudyTimeQuestProperties properties = new StudyTimeQuestProperties(12_600, 41_400);

        assertEquals(12_600, properties.minTargetSeconds());
        assertEquals(41_400, properties.maxTargetSeconds());
    }
}
