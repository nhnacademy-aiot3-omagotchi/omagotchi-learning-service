package site.omagotchi.learningservice.prediction.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.global.time.AggregationDateTime;
import site.omagotchi.learningservice.prediction.application.dto.StudyTimePredictionRequest;
import site.omagotchi.learningservice.prediction.application.exception.PredictionClientException;
import site.omagotchi.learningservice.prediction.application.port.PredictionClient;
import site.omagotchi.learningservice.prediction.application.port.PredictionFeatureSnapshotReader;
import site.omagotchi.learningservice.prediction.application.result.PredictionFeatureSnapshot;
import site.omagotchi.learningservice.prediction.application.result.StudyTimePredictionResult;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudyTimePredictionService {

    private final CohortAccessService cohortAccessService;
    private final PredictionFeatureSnapshotReader featureSnapshotReader;
    private final StudyTimePredictionRequestAssembler requestAssembler;
    private final PredictionClient predictionClient;
    private final Clock clock;

    public StudyTimePredictionResult predict(
            UUID userId,
            Long cohortId,
            String requestId
    ) {
        long startedAtNanos = System.nanoTime();
        String userIdMasked = maskUserId(userId);

        // 예측의 모든 원천값은 API가 받은 cohort의 활성 소속에 귀속시킨다.
        Long cohortMembershipId = cohortAccessService.requireActiveMembershipId(cohortId, userId);

        // 현재 KST 집계일이 targetDate이고, 모델 입력은 그 전날인 featureDate까지 사용한다.
        LocalDate targetDate = AggregationDateTime.aggregationDate(clock.instant());
        LocalDate featureDate = targetDate.minusDays(1L);

        PredictionFeatureSnapshot snapshot = featureSnapshotReader.read(
                userId,
                cohortId,
                cohortMembershipId,
                featureDate
        );
        StudyTimePredictionRequest request = requestAssembler.assemble(snapshot);

        try {
            // 이 API는 예측값 조회만 수행하므로 의존 서비스 실패를 규칙값으로 숨기는 fallback은 적용하지 않는다.
            // prediction 전용 응답 필드의 의미 검증과 퀘스트 목표 정책은 별도 유스케이스에서 결정한다.
            StudyTimePredictionResult result = predictionClient.predict(request, requestId);

            log.info(
                    "공부시간 예측을 완료했습니다. "
                            + "userIdMasked={}, cohortId={}, featureDate={}, "
                            + "elapsedMs={}, modelVersion={}",
                    userIdMasked,
                    cohortId,
                    featureDate,
                    elapsedMillis(startedAtNanos),
                    result.modelVersion()
            );
            return result;
        } catch (PredictionClientException exception) {
            log.warn(
                    "공부시간 예측에 실패했습니다. "
                            + "userIdMasked={}, cohortId={}, featureDate={}, "
                            + "reason={}, elapsedMs={}",
                    userIdMasked,
                    cohortId,
                    featureDate,
                    exception.getReason(),
                    elapsedMillis(startedAtNanos)
            );

            // 이 계층은 호출 실패를 기록만 하고 규칙값으로 대체하지 않는다.
            // 공개 API는 실패를 그대로 반환하고 퀘스트 경로만 상위 resolver에서 폴백한다.
            throw exception;
        }
    }

    private String maskUserId(UUID userId) {
        if (Objects.isNull(userId)) {
            return "none";
        }
        return userId.toString().substring(0, 8);
    }

    private long elapsedMillis(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }
}
