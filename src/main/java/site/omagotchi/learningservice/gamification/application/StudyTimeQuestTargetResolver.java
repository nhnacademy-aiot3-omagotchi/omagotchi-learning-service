package site.omagotchi.learningservice.gamification.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.gamification.domain.QuestTargetPolicy;
import site.omagotchi.learningservice.gamification.domain.StudyTimeQuestTarget;
import site.omagotchi.learningservice.prediction.application.StudyTimePredictionService;
import site.omagotchi.learningservice.prediction.application.result.StudyTimePredictionResult;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class StudyTimeQuestTargetResolver {

    private final StudyTimePredictionService studyTimePredictionService;
    private final UserStudySecondsReader userStudySecondsReader;
    private final StudyTimeQuestProperties properties;

    public StudyTimeQuestTarget resolve(UUID userId, LocalDate questDate) {
        Optional<Long> cohortId = userStudySecondsReader.findActiveCohortId(userId);
        if (cohortId.isEmpty()) {
            // 활성 소속이 없으면 예측도 규칙도 계산할 수 없다.
            return defaultTarget();
        }

        Long activeCohortId = cohortId.get();
        if (!userStudySecondsReader.hasStudyRecordBefore(userId, activeCohortId, questDate)) {
            // 확정 학습 기록이 하나도 없으면 모델 입력이 전부 0이라 누구에게나 같은 값이 나온다.
            // 그건 예측이 아니고, 규칙(B2)도 등원일이 없어 값을 못 내므로 바로 기본값으로 간다.
            return defaultTarget();
        }

        return predictedTarget(userId, activeCohortId)
                .or(() -> ruleTarget(userId, activeCohortId, questDate))
                .orElseGet(this::defaultTarget);
    }

    private Optional<StudyTimeQuestTarget> predictedTarget(UUID userId, Long cohortId) {
        try {
            StudyTimePredictionResult result = studyTimePredictionService.predict(userId, cohortId, null);
            if (result == null || result.predictedStudyHours() == null) {
                return Optional.empty();
            }
            int targetSeconds = QuestTargetPolicy.targetSecondsOf(
                    result.predictedStudyHours(),
                    properties.challengeCoefficient(),
                    properties.minTargetSeconds(),
                    properties.maxTargetSeconds()
            );
            return Optional.of(StudyTimeQuestTarget.model(targetSeconds, result.modelVersion()));
        } catch (RuntimeException exception) {
            // 콜드스타트 하드 실패와 prediction-service 장애를 여기서 같은 방식으로 흡수한다.
            log.warn("예측으로 학습 시간 퀘스트 목표를 산정하지 못해 규칙으로 폴백한다. userId={}", userId, exception);
            return Optional.empty();
        }
    }

    private Optional<StudyTimeQuestTarget> ruleTarget(UUID userId, Long cohortId, LocalDate questDate) {
        try {
            long average = userStudySecondsReader.recentAttendedAverageSeconds(userId, cohortId, questDate);
            if (average <= 0) {
                // 등원 이력이 없으면 B2도 값을 내지 못한다.
                return Optional.empty();
            }
            long challenged = Math.round(average * properties.challengeCoefficient());
            return Optional.of(StudyTimeQuestTarget.rule(clamp(challenged)));
        } catch (RuntimeException exception) {
            log.warn("규칙으로 학습 시간 퀘스트 목표를 산정하지 못해 기본값으로 폴백한다. userId={}", userId, exception);
            return Optional.empty();
        }
    }

    private StudyTimeQuestTarget defaultTarget() {
        return StudyTimeQuestTarget.fallback(properties.minTargetSeconds());
    }

    private int clamp(long targetSeconds) {
        return QuestTargetPolicy.clamp(
                targetSeconds,
                properties.minTargetSeconds(),
                properties.maxTargetSeconds()
        );
    }
}
