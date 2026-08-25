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

        // 이 API는 예측값 조회만 수행하므로 의존 서비스 실패를 규칙값으로 숨기는 fallback은 적용하지 않는다.
        // prediction 전용 응답 필드의 의미 검증과 퀘스트 목표 정책은 별도 유스케이스에서 결정한다.
        return predictionClient.predict(request, requestId);
    }
}
