package site.omagotchi.learningservice.gamification.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.gamification.domain.QuestTargetPolicy;
import site.omagotchi.learningservice.gamification.domain.StudyTimeQuestTarget;
import site.omagotchi.learningservice.prediction.application.StudyTimePredictionService;
import site.omagotchi.learningservice.prediction.application.exception.PredictionClientException;
import site.omagotchi.learningservice.prediction.application.result.StudyTimePredictionResult;

import java.time.LocalDate;
import java.util.Objects;
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
            log.info(
                    "활성 기수 소속이 없어 학습 시간 퀘스트 목표를 기본값으로 산정합니다. "
                            + "userIdMasked={}, questDate={}, targetSeconds={}",
                    maskUserId(userId),
                    questDate,
                    properties.minTargetSeconds()
            );
            return defaultTarget();
        }

        Long activeCohortId = cohortId.get();
        if (!userStudySecondsReader.hasStudyRecordBefore(userId, activeCohortId, questDate)) {
            // 확정 학습 기록이 하나도 없으면 모델 입력이 전부 0이라 누구에게나 같은 값이 나온다.
            // 그건 예측이 아니고, 규칙(B2)도 등원일이 없어 값을 못 내므로 바로 기본값으로 간다.
            log.info(
                    "확정 학습 기록이 없어 학습 시간 퀘스트 목표를 기본값으로 산정합니다. "
                            + "userIdMasked={}, cohortId={}, questDate={}, targetSeconds={}",
                    maskUserId(userId),
                    activeCohortId,
                    questDate,
                    properties.minTargetSeconds()
            );
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
                log.warn(
                        "prediction-service가 유효한 학습 시간 예측값을 반환하지 않아 규칙으로 폴백합니다. "
                                + "userIdMasked={}, cohortId={}",
                        maskUserId(userId),
                        cohortId
                );
                return Optional.empty();
            }
            int targetSeconds = QuestTargetPolicy.targetSecondsOf(
                    result.predictedStudyHours(),
                    properties.challengeCoefficient(),
                    properties.minTargetSeconds(),
                    properties.maxTargetSeconds()
            );
            log.info(
                    "예측으로 학습 시간 퀘스트 목표를 산정했습니다. "
                            + "userIdMasked={}, cohortId={}, targetSeconds={}, modelVersion={}",
                    maskUserId(userId),
                    cohortId,
                    targetSeconds,
                    result.modelVersion()
            );
            return Optional.of(StudyTimeQuestTarget.model(targetSeconds, result.modelVersion()));
        } catch (PredictionClientException exception) {
            log.warn(
                    "prediction-service 호출 실패로 학습 시간 퀘스트 목표를 규칙으로 폴백합니다. "
                            + "userIdMasked={}, cohortId={}, reason={}",
                    maskUserId(userId),
                    cohortId,
                    exception.getReason(),
                    exception
            );
            return Optional.empty();
        } catch (RuntimeException exception) {
            // 예측 목표 계산 중 발생한 예상 밖 오류도 퀘스트 발급을 막지 않고 규칙으로 흡수한다.
            log.warn(
                    "예측 목표 산정 중 예상하지 못한 오류가 발생해 규칙으로 폴백합니다. "
                            + "userIdMasked={}, cohortId={}, exception={}",
                    maskUserId(userId),
                    cohortId,
                    exception.getClass().getName(),
                    exception
            );
            return Optional.empty();
        }
    }

    private Optional<StudyTimeQuestTarget> ruleTarget(UUID userId, Long cohortId, LocalDate questDate) {
        try {
            long average = userStudySecondsReader.recentAttendedAverageSeconds(userId, cohortId, questDate);
            if (average <= 0) {
                // 등원 이력이 없으면 B2도 값을 내지 못한다.
                log.info(
                        "최근 등원 학습 이력이 없어 학습 시간 퀘스트 목표를 기본값으로 산정합니다. "
                                + "userIdMasked={}, cohortId={}, questDate={}, targetSeconds={}",
                        maskUserId(userId),
                        cohortId,
                        questDate,
                        properties.minTargetSeconds()
                );
                return Optional.empty();
            }
            long challenged = Math.round(average * properties.challengeCoefficient());
            int targetSeconds = clamp(challenged);
            log.info(
                    "규칙으로 학습 시간 퀘스트 목표를 산정했습니다. "
                            + "userIdMasked={}, cohortId={}, questDate={}, targetSeconds={}",
                    maskUserId(userId),
                    cohortId,
                    questDate,
                    targetSeconds
            );
            return Optional.of(StudyTimeQuestTarget.rule(targetSeconds));
        } catch (RuntimeException exception) {
            log.warn(
                    "규칙으로 학습 시간 퀘스트 목표를 산정하지 못해 기본값으로 폴백합니다. "
                            + "userIdMasked={}, cohortId={}, questDate={}, exception={}",
                    maskUserId(userId),
                    cohortId,
                    questDate,
                    exception.getClass().getName(),
                    exception
            );
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

    private String maskUserId(UUID userId) {
        if (Objects.isNull(userId)) {
            return "none";
        }
        return userId.toString().substring(0, 8);
    }
}
