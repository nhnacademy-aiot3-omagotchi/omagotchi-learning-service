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
                            + "사용자(userIdMasked)={}, 퀘스트 날짜(questDate)={}, "
                            + "기본목표(targetSeconds)={}초({})",
                    maskUserId(userId),
                    questDate,
                    properties.minTargetSeconds(),
                    formatDuration(properties.minTargetSeconds())
            );
            return defaultTarget();
        }

        Long activeCohortId = cohortId.get();
        if (!userStudySecondsReader.hasStudyRecordBefore(userId, activeCohortId, questDate)) {
            // 확정 학습 기록이 하나도 없으면 모델 입력이 전부 0이라 누구에게나 같은 값이 나온다.
            // 그건 예측이 아니고, 규칙(B2)도 등원일이 없어 값을 못 내므로 바로 기본값으로 간다.
            log.info(
                    "확정 학습 기록이 없어 학습 시간 퀘스트 목표를 기본값으로 산정합니다. "
                            + "사용자(userIdMasked)={}, 기수(cohortId)={}, "
                            + "퀘스트 날짜(questDate)={}, 기본목표(targetSeconds)={}초({})",
                    maskUserId(userId),
                    activeCohortId,
                    questDate,
                    properties.minTargetSeconds(),
                    formatDuration(properties.minTargetSeconds())
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
                                + "사용자(userIdMasked)={}, 기수(cohortId)={}",
                        maskUserId(userId),
                        cohortId
                );
                return Optional.empty();
            }
            QuestTargetPolicy.Calculation calculation = QuestTargetPolicy.calculate(
                    result.predictedStudyHours(),
                    properties.challengeCoefficient(),
                    properties.minTargetSeconds(),
                    properties.maxTargetSeconds()
            );
            log.info(
                    "학습 시간 퀘스트 목표 산정: "
                            + "예측 {}시간 → 도전계수 {}배 → 계산 {} → {} → 최종 {} "
                            + "| 사용자(userIdMasked)={}, 기수(cohortId)={}, "
                            + "계산초(calculatedTargetSeconds)={}, "
                            + "정책범위(minTargetSeconds/maxTargetSeconds)={}/{}, "
                            + "보정(adjustment)={}({}), 최종초(targetSeconds)={}, "
                            + "모델(modelVersion)={}",
                    result.predictedStudyHours(),
                    properties.challengeCoefficient(),
                    formatDuration(calculation.calculatedTargetSeconds()),
                    adjustmentDescription(calculation.adjustment()),
                    formatDuration(calculation.targetSeconds()),
                    maskUserId(userId),
                    cohortId,
                    calculation.calculatedTargetSeconds(),
                    properties.minTargetSeconds(),
                    properties.maxTargetSeconds(),
                    adjustmentDescription(calculation.adjustment()),
                    calculation.adjustment(),
                    calculation.targetSeconds(),
                    result.modelVersion()
            );
            return Optional.of(StudyTimeQuestTarget.model(
                    calculation.targetSeconds(),
                    result.modelVersion()
            ));
        } catch (PredictionClientException exception) {
            log.warn(
                    "prediction-service 호출 실패로 학습 시간 퀘스트 목표를 규칙으로 폴백합니다. "
                            + "사용자(userIdMasked)={}, 기수(cohortId)={}, "
                            + "실패사유(reason)={}({})",
                    maskUserId(userId),
                    cohortId,
                    predictionFailureDescription(exception.getReason()),
                    exception.getReason(),
                    exception
            );
            return Optional.empty();
        } catch (RuntimeException exception) {
            // 예측 목표 계산 중 발생한 예상 밖 오류도 퀘스트 발급을 막지 않고 규칙으로 흡수한다.
            log.warn(
                    "예측 목표 산정 중 예상하지 못한 오류가 발생해 규칙으로 폴백합니다. "
                            + "사용자(userIdMasked)={}, 기수(cohortId)={}, "
                            + "예외유형(exception)={}",
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
                                + "사용자(userIdMasked)={}, 기수(cohortId)={}, "
                                + "퀘스트 날짜(questDate)={}, 기본목표(targetSeconds)={}초({})",
                        maskUserId(userId),
                        cohortId,
                        questDate,
                        properties.minTargetSeconds(),
                        formatDuration(properties.minTargetSeconds())
                );
                return Optional.empty();
            }
            long challenged = Math.round(average * properties.challengeCoefficient());
            int targetSeconds = clamp(challenged);
            QuestTargetPolicy.Adjustment adjustment = QuestTargetPolicy.adjustmentOf(
                    challenged,
                    properties.minTargetSeconds(),
                    properties.maxTargetSeconds()
            );
            log.info(
                    "규칙 기반 학습 시간 퀘스트 목표 산정: "
                            + "최근 등원일 평균 {} → 도전계수 {}배 → 계산 {} → {} → 최종 {} "
                            + "| 사용자(userIdMasked)={}, 기수(cohortId)={}, "
                            + "퀘스트 날짜(questDate)={}, 평균초(attendedAverageSeconds)={}, "
                            + "계산초(calculatedTargetSeconds)={}, "
                            + "정책범위(minTargetSeconds/maxTargetSeconds)={}/{}, "
                            + "보정(adjustment)={}({}), 최종초(targetSeconds)={}",
                    formatDuration(average),
                    properties.challengeCoefficient(),
                    formatDuration(challenged),
                    adjustmentDescription(adjustment),
                    formatDuration(targetSeconds),
                    maskUserId(userId),
                    cohortId,
                    questDate,
                    average,
                    challenged,
                    properties.minTargetSeconds(),
                    properties.maxTargetSeconds(),
                    adjustmentDescription(adjustment),
                    adjustment,
                    targetSeconds
            );
            return Optional.of(StudyTimeQuestTarget.rule(targetSeconds));
        } catch (RuntimeException exception) {
            log.warn(
                    "규칙으로 학습 시간 퀘스트 목표를 산정하지 못해 기본값으로 폴백합니다. "
                            + "사용자(userIdMasked)={}, 기수(cohortId)={}, "
                            + "퀘스트 날짜(questDate)={}, 예외유형(exception)={}",
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

    private String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = seconds % 3600 / 60;
        long remainingSeconds = seconds % 60;

        if (remainingSeconds == 0) {
            return "%d시간 %d분".formatted(hours, minutes);
        }
        return "%d시간 %d분 %d초".formatted(hours, minutes, remainingSeconds);
    }

    private String adjustmentDescription(QuestTargetPolicy.Adjustment adjustment) {
        return switch (adjustment) {
            case NONE -> "보정 없음";
            case MIN_CLAMP -> "최소 목표 %s 적용".formatted(
                    formatDuration(properties.minTargetSeconds())
            );
            case MAX_CLAMP -> "최대 목표 %s 적용".formatted(
                    formatDuration(properties.maxTargetSeconds())
            );
        };
    }

    private String predictionFailureDescription(PredictionClientException.Reason reason) {
        return switch (reason) {
            case BAD_RESPONSE -> "잘못된 응답";
            case UNAVAILABLE -> "서비스 연결 불가";
            case TIMEOUT -> "시간 초과";
        };
    }

    private String maskUserId(UUID userId) {
        if (Objects.isNull(userId)) {
            return "none";
        }
        return userId.toString().substring(0, 8);
    }
}
