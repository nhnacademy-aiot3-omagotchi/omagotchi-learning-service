package site.omagotchi.learningservice.prediction.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.global.time.AggregationDateTime;
import site.omagotchi.learningservice.prediction.application.dto.StudyTimePredictionRequest;
import site.omagotchi.learningservice.prediction.application.port.PredictionClient;
import site.omagotchi.learningservice.prediction.application.port.PredictionFeatureSnapshotReader;
import site.omagotchi.learningservice.prediction.application.result.PredictionFeatureSnapshot;
import site.omagotchi.learningservice.prediction.application.result.StudyTimePredictionResult;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

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
        // 예측의 모든 원천값은 API가 받은 cohort의 활성 소속에 귀속시킨다.
        Long cohortMembershipId = cohortAccessService.requireActiveMembershipId(cohortId, userId);

        // baseDate와 출결 마감 판정이 서로 다른 시각을 보지 않도록 Clock을 한 번만 읽는다.
        Instant observedAt = clock.instant();
        LocalDate baseDate = AggregationDateTime.aggregationDate(observedAt);

        PredictionFeatureSnapshot snapshot = featureSnapshotReader.read(
                userId,
                cohortId,
                cohortMembershipId,
                baseDate,
                observedAt
        );
        StudyTimePredictionRequest request = requestAssembler.assemble(snapshot);

        StudyTimePredictionResult result = predictionClient.predict(request, requestId);

        // TODO: prediction-service 응답 검증, 폴백 및 퀘스트 목표 정책은 후속 구현한다.
        return result;
    }
}
