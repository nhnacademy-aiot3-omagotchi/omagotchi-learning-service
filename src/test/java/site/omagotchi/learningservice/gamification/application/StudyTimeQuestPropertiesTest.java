package site.omagotchi.learningservice.gamification.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("학습 시간 퀘스트 정책 설정")
class StudyTimeQuestPropertiesTest {

    @Test
    @DisplayName("도전 계수가 NaN이면 기동에 실패한다")
    void rejectsNaNCoefficient() {
        // NaN은 <= 0 검사를 통과해 버리고, 이후 Math.round(NaN)이 0이 되어
        // 모든 사용자의 목표가 조용히 하한으로 굳는다.
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new StudyTimeQuestProperties(Double.NaN, 12_600, 41_400)
        );

        assertEquals(
                "gamification.study-time-quest.challenge-coefficient은 유한한 양수여야 합니다.",
                exception.getMessage()
        );
    }

    @Test
    @DisplayName("도전 계수가 무한대면 기동에 실패한다")
    void rejectsInfiniteCoefficient() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new StudyTimeQuestProperties(Double.POSITIVE_INFINITY, 12_600, 41_400)
        );
    }

    @Test
    @DisplayName("값이 비어 있거나 0 이하면 기동에 실패한다")
    void rejectsMissingOrNonPositiveValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new StudyTimeQuestProperties(null, 12_600, 41_400));
        assertThrows(IllegalArgumentException.class,
                () -> new StudyTimeQuestProperties(1.1, 0, 41_400));
        assertThrows(IllegalArgumentException.class,
                () -> new StudyTimeQuestProperties(1.1, 12_600, -1));
    }

    @Test
    @DisplayName("하한이 상한보다 크면 기동에 실패한다")
    void rejectsInvertedBounds() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new StudyTimeQuestProperties(1.1, 41_400, 12_600)
        );

        assertEquals(
                "gamification.study-time-quest.min-target-seconds는 max-target-seconds보다 클 수 없습니다.",
                exception.getMessage()
        );
    }

    @Test
    @DisplayName("결정된 정책값은 그대로 통과한다")
    void acceptsDecidedPolicyValues() {
        StudyTimeQuestProperties properties = new StudyTimeQuestProperties(1.1, 12_600, 41_400);

        assertEquals(1.1, properties.challengeCoefficient());
        assertEquals(12_600, properties.minTargetSeconds());
        assertEquals(41_400, properties.maxTargetSeconds());
    }
}
