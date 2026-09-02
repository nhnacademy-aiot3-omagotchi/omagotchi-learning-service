package site.omagotchi.learningservice.gamification.domain;

/**
 * 퀘스트 목표를 무엇으로 산정했는지 구분한다.
 *
 * <p>모델 결과와 규칙 결과를 섞어 두면 예측 품질을 사후에 평가할 수 없으므로
 * 출처를 행에 남긴다(ADR prediction/0002).
 */
public enum QuestTargetSource {
    TEMPLATE, // 정적 템플릿의 목표 횟수를 그대로 사용
    MODEL, // prediction-service 예측값에 정책을 적용
    RULE_B2, // 예측 실패 시 최근 7 등원일 평균 규칙
    DEFAULT // 규칙도 값을 낼 수 없을 때의 기본 목표
}
