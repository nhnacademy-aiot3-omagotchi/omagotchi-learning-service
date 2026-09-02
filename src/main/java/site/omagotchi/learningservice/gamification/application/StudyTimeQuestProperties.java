package site.omagotchi.learningservice.gamification.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 예측 기반 학습 시간 퀘스트의 정책값.
 *
 * <p>정책값의 변경 주기는 모델의 변경 주기와 다르므로 코드가 아니라 설정으로 둔다
 * (ADR prediction/0002). prediction-service의 MAX_STUDY_H는 모델 출력 경계이지
 * 퀘스트 상한이 아니므로, 여기의 상한은 그 값과 독립적으로 선언한다.
 */
@ConfigurationProperties(prefix = "gamification.study-time-quest")
public record StudyTimeQuestProperties(
        Double challengeCoefficient,
        Integer minTargetSeconds,
        Integer maxTargetSeconds
) {

    public StudyTimeQuestProperties {
        requirePositive(challengeCoefficient, "gamification.study-time-quest.challenge-coefficient");
        requirePositive(minTargetSeconds, "gamification.study-time-quest.min-target-seconds");
        requirePositive(maxTargetSeconds, "gamification.study-time-quest.max-target-seconds");
        if (minTargetSeconds > maxTargetSeconds) {
            throw new IllegalArgumentException(
                    "gamification.study-time-quest.min-target-seconds는 max-target-seconds보다 클 수 없습니다."
            );
        }
    }

    private static void requirePositive(Number value, String propertyName) {
        // NaN은 어떤 비교에도 false를 내므로 <= 0 검사만으로는 통과한다.
        // 그대로 흘러가면 Math.round(NaN)이 0이 되어 목표가 조용히 하한으로 굳는다.
        if (value == null || !Double.isFinite(value.doubleValue()) || value.doubleValue() <= 0) {
            throw new IllegalArgumentException(propertyName + "은 유한한 양수여야 합니다.");
        }
    }
}
