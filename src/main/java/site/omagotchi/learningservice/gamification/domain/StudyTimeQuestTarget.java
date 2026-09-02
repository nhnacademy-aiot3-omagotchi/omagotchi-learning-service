package site.omagotchi.learningservice.gamification.domain;

/**
 * 산정이 끝난 학습 시간 퀘스트 목표.
 *
 * @param targetSeconds 상·하한을 통과한 목표 시간(초)
 * @param source        목표를 무엇으로 산정했는지
 * @param modelVersion  MODEL로 산정한 경우의 모델 버전, 그 외에는 null
 */
public record StudyTimeQuestTarget(
        int targetSeconds,
        QuestTargetSource source,
        String modelVersion
) {

    public StudyTimeQuestTarget {
        if (targetSeconds <= 0) {
            throw new IllegalArgumentException("목표 시간은 양수여야 합니다.");
        }
        if (source == null) {
            throw new IllegalArgumentException("목표 산정 출처는 필수입니다.");
        }
        if (modelVersion != null && source != QuestTargetSource.MODEL) {
            // ck_user_daily_quests_model_version 제약과 같은 규칙을 도메인에서 먼저 막는다.
            throw new IllegalArgumentException("모델 버전은 MODEL 출처에서만 기록할 수 있습니다.");
        }
    }

    public static StudyTimeQuestTarget model(int targetSeconds, String modelVersion) {
        return new StudyTimeQuestTarget(targetSeconds, QuestTargetSource.MODEL, modelVersion);
    }

    public static StudyTimeQuestTarget rule(int targetSeconds) {
        return new StudyTimeQuestTarget(targetSeconds, QuestTargetSource.RULE_B2, null);
    }

    public static StudyTimeQuestTarget fallback(int targetSeconds) {
        return new StudyTimeQuestTarget(targetSeconds, QuestTargetSource.DEFAULT, null);
    }
}
