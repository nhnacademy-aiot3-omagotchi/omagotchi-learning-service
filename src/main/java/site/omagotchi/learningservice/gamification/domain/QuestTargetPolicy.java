package site.omagotchi.learningservice.gamification.domain;

/**
 * 예측값을 퀘스트 목표 시간으로 바꾸는 정책
 * <p>
 * 예측 공부 시간을 초로 환산한 뒤 상/하한으로 클립한다. 예측값에는 별도 계수를 곱하지 않는다.
 * 퀘스트의 기준은 도전이 아니라 꾸준함이라, 목표는 모델이 예상한 평소 수준 그대로이다.
 * 상/하한은 prediction-service의 모델 출력 경계 [0, 11.5]h와 무관한 퀘스트 정책값이다.
 */
public final class QuestTargetPolicy {

    private QuestTargetPolicy() {
    }

    private static final int SECONDS_PER_HOUR = 3600;

    /**
     * 예측값을 초로 환산한 중간값과 상·하한 보정 결과를 함께 반환한다.
     */
    public static Calculation calculate(
            double predictedStudyHours,
            int minTargetSeconds,
            int maxTargetSeconds
    ) {
        long calculatedTargetSeconds = Math.round(predictedStudyHours * SECONDS_PER_HOUR);
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
