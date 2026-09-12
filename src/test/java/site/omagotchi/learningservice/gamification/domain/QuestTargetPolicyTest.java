package site.omagotchi.learningservice.gamification.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("퀘스트 목표 시간 정책")
class QuestTargetPolicyTest {

    private static final int MIN_SECONDS = 12_600; // 3h30m
    private static final int MAX_SECONDS = 41_400; // 11h30m

    @Test
    @DisplayName("예측값을 계수 없이 그대로 초로 환산한다")
    void convertsPredictionToSecondsWithoutCoefficient() {
        // 4h = 14400s. 예측값이 곧 목표다.
        QuestTargetPolicy.Calculation calculation = QuestTargetPolicy.calculate(
                4.0,
                MIN_SECONDS,
                MAX_SECONDS
        );

        assertAll(
                () -> assertEquals(14_400, calculation.calculatedTargetSeconds()),
                () -> assertEquals(14_400, calculation.targetSeconds()),
                () -> assertEquals(QuestTargetPolicy.Adjustment.NONE, calculation.adjustment())
        );
    }

    @Test
    @DisplayName("모델 출력 상한 11.5h는 퀘스트 상한과 같은 값이라 보정 없이 통과한다")
    void modelOutputBoundaryPassesWithoutClamp() {
        // 11.5h = 41400s = MAX_SECONDS. 두 값이 같은 것은 우연이며, 이 테스트는 그 우연을 명시한다.
        // 퀘스트 상한을 바꾸면 이 테스트가 깨져 MODEL 경로의 상한 동작을 다시 확인하게 된다.
        QuestTargetPolicy.Calculation calculation = QuestTargetPolicy.calculate(
                11.5,
                MIN_SECONDS,
                MAX_SECONDS
        );

        assertAll(
                () -> assertEquals(MAX_SECONDS, calculation.calculatedTargetSeconds()),
                () -> assertEquals(MAX_SECONDS, calculation.targetSeconds()),
                () -> assertEquals(QuestTargetPolicy.Adjustment.NONE, calculation.adjustment())
        );
    }

    @Test
    @DisplayName("퀘스트 상한은 모델 경계와 독립이라 그보다 큰 입력은 상한으로 자른다")
    void clampsInputAboveQuestMaximum() {
        // 정책은 prediction-service의 [0, 11.5]h 보정에 기대지 않는다.
        // 12h = 43200s > 41400s
        QuestTargetPolicy.Calculation calculation = QuestTargetPolicy.calculate(
                12.0,
                MIN_SECONDS,
                MAX_SECONDS
        );

        assertAll(
                () -> assertEquals(43_200, calculation.calculatedTargetSeconds()),
                () -> assertEquals(MAX_SECONDS, calculation.targetSeconds()),
                () -> assertEquals(
                        QuestTargetPolicy.Adjustment.MAX_CLAMP,
                        calculation.adjustment()
                )
        );
    }

    @Test
    @DisplayName("예측이 0이어도 하한 아래로 내려가지 않는다")
    void neverFallsBelowMinimum() {
        // 0초 목표는 ck_user_daily_quests_target_count(target_count > 0) 위반으로 저장에 실패한다.
        QuestTargetPolicy.Calculation calculation = QuestTargetPolicy.calculate(
                0.0,
                MIN_SECONDS,
                MAX_SECONDS
        );

        assertAll(
                () -> assertEquals(0, calculation.calculatedTargetSeconds()),
                () -> assertEquals(MIN_SECONDS, calculation.targetSeconds()),
                () -> assertEquals(
                        QuestTargetPolicy.Adjustment.MIN_CLAMP,
                        calculation.adjustment()
                )
        );
    }

    @Test
    @DisplayName("하한에 못 미치는 예측은 하한으로 올린다")
    void raisesSmallPredictionToMinimum() {
        // 1h = 3600s < 12600s
        QuestTargetPolicy.Calculation calculation = QuestTargetPolicy.calculate(
                1.0,
                MIN_SECONDS,
                MAX_SECONDS
        );

        assertAll(
                () -> assertEquals(3_600, calculation.calculatedTargetSeconds()),
                () -> assertEquals(MIN_SECONDS, calculation.targetSeconds()),
                () -> assertEquals(
                        QuestTargetPolicy.Adjustment.MIN_CLAMP,
                        calculation.adjustment()
                )
        );
    }

    @Test
    @DisplayName("규칙·기본값으로 산정한 초도 같은 상하한을 통과한다")
    void clampsRuleBasedSecondsWithSameBounds() {
        assertEquals(MIN_SECONDS, QuestTargetPolicy.clamp(0L, MIN_SECONDS, MAX_SECONDS));
        assertEquals(MAX_SECONDS, QuestTargetPolicy.clamp(999_999L, MIN_SECONDS, MAX_SECONDS));
        assertEquals(20_000, QuestTargetPolicy.clamp(20_000L, MIN_SECONDS, MAX_SECONDS));
    }
}
