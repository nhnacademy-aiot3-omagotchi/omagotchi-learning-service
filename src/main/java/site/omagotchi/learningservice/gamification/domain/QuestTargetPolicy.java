package site.omagotchi.learningservice.gamification.domain;

/**
 * 예측값을 퀘스트 목표 시간으로 바꾸는 정책.
 *
 * <p>도전 계수를 곱한 뒤 상·하한으로 클립한다. 순서를 뒤집으면 상한이 상한 역할을 하지 못한다.
 * prediction-service가 이미 출력을 [0, 11.5]h로 보정해 주므로 계수를 곱하면 상한을 넘길 수 있고,
 * 그 경계 처리는 learning-service의 책임이다(ADR prediction/0002).
 */
public final class QuestTargetPolicy {

    private QuestTargetPolicy() {
    }

    private static final int SECONDS_PER_HOUR = 3600;

    /**
     * 예측값에 도전 계수를 적용한 중간값과 상·하한 보정 결과를 함께 반환한다.
     */
    public static Calculation calculate(
            double predictedStudyHours,
            double challengeCoefficient,
            int minTargetSeconds,
            int maxTargetSeconds
    ) {
        double challenged = predictedStudyHours * challengeCoefficient * SECONDS_PER_HOUR;
        long calculatedTargetSeconds = Math.round(challenged);
        int targetSeconds = clamp(calculatedTargetSeconds, minTargetSeconds, maxTargetSeconds);

        Adjustment adjustment = adjustmentOf(
                calculatedTargetSeconds,
                minTargetSeconds,
                maxTargetSeconds
        );

        return new Calculation(calculatedTargetSeconds, targetSeconds, adjustment);
    }

    /**
     * 예측 없이 규칙·기본값으로 산정한 초도 같은 상·하한을 통과시킨다.
     * 하한을 통과시키지 않으면 target_count &gt; 0 제약을 위반하는 0초 목표가 저장될 수 있다.
     */
    public static int clamp(long targetSeconds, int minTargetSeconds, int maxTargetSeconds) {
        long bounded = Math.min(Math.max(targetSeconds, minTargetSeconds), maxTargetSeconds);
        return Math.toIntExact(bounded);
    }

    public static Adjustment adjustmentOf(
            long calculatedTargetSeconds,
            int minTargetSeconds,
            int maxTargetSeconds
    ) {
        if (calculatedTargetSeconds < minTargetSeconds) {
            return Adjustment.MIN_CLAMP;
        }
        if (calculatedTargetSeconds > maxTargetSeconds) {
            return Adjustment.MAX_CLAMP;
        }
        return Adjustment.NONE;
    }

    public enum Adjustment {
        NONE,
        MIN_CLAMP,
        MAX_CLAMP
    }

    public record Calculation(
            long calculatedTargetSeconds,
            int targetSeconds,
            Adjustment adjustment
    ) {
    }
}
