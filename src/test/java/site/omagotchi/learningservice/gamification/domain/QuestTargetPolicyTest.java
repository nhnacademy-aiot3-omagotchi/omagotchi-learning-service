package site.omagotchi.learningservice.gamification.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("퀘스트 목표 시간 정책")
class QuestTargetPolicyTest {

    private static final double COEFFICIENT = 1.1;
    private static final int MIN_SECONDS = 12_600; // 3h30m
    private static final int MAX_SECONDS = 41_400; // 11h30m

    @Test
    @DisplayName("예측값에 도전 계수를 곱한 뒤 초로 환산한다")
    void multipliesPredictionByChallengeCoefficient() {
        // 4h * 1.1 = 4.4h = 15840s
        QuestTargetPolicy.Calculation calculation = QuestTargetPolicy.calculate(
                4.0,
                COEFFICIENT,
                MIN_SECONDS,
                MAX_SECONDS
        );

        assertAll(
                () -> assertEquals(15_840, calculation.calculatedTargetSeconds()),
                () -> assertEquals(15_840, calculation.targetSeconds()),
                () -> assertEquals(QuestTargetPolicy.Adjustment.NONE, calculation.adjustment())
        );
    }

    @Test
    @DisplayName("계수를 곱한 뒤에 상한으로 자른다")
    void clampsAfterMultiplyingNotBefore() {
        // 모델 출력 상한 11.5h에 계수를 곱하면 12.65h라 퀘스트 상한을 넘는다.
        // 먼저 자르고 곱하면 45540s가 되어 상한이 상한 역할을 하지 못한다.
        QuestTargetPolicy.Calculation calculation = QuestTargetPolicy.calculate(
                11.5,
                COEFFICIENT,
                MIN_SECONDS,
                MAX_SECONDS
        );

        assertAll(
                () -> assertEquals(45_540, calculation.calculatedTargetSeconds()),
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
                COEFFICIENT,
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
    @DisplayName("계수를 곱해도 하한에 못 미치면 하한으로 올린다")
    void raisesSmallPredictionToMinimum() {
        // 1h * 1.1 = 3960s < 12600s
        QuestTargetPolicy.Calculation calculation = QuestTargetPolicy.calculate(
                1.0,
                COEFFICIENT,
                MIN_SECONDS,
                MAX_SECONDS
        );

        assertAll(
                () -> assertEquals(3_960, calculation.calculatedTargetSeconds()),
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
