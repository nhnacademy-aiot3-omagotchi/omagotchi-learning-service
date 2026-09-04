package site.omagotchi.learningservice.prediction.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.cohort.application.CohortErrorCode;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.prediction.application.dto.StudyTimePredictionRequest;
import site.omagotchi.learningservice.prediction.application.exception.PredictionClientException;
import site.omagotchi.learningservice.prediction.application.port.PredictionClient;
import site.omagotchi.learningservice.prediction.application.port.PredictionFeatureSnapshotReader;
import site.omagotchi.learningservice.prediction.application.result.PredictionFeatureSnapshot;
import site.omagotchi.learningservice.prediction.application.result.PredictionFeatureSnapshot.AttendanceHistory;
import site.omagotchi.learningservice.prediction.application.result.PredictionFeatureSnapshot.GamificationHistory;
import site.omagotchi.learningservice.prediction.application.result.PredictionFeatureSnapshot.StudyHistory;
import site.omagotchi.learningservice.prediction.application.result.StudyTimePredictionResult;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("공부시간 예측 흐름")
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class StudyTimePredictionServiceTest {

    private static final UUID USER_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000001"
    );
    private static final Long COHORT_ID = 10L;
    private static final Long COHORT_MEMBERSHIP_ID = 20L;
    private static final String REQUEST_ID = "prediction-request-id";
    private static final Instant CURRENT_TIME = Instant.parse("2000-01-01T00:00:00Z");
    private static final LocalDate TARGET_DATE = LocalDate.parse("2000-01-01");
    private static final LocalDate FEATURE_DATE = TARGET_DATE.minusDays(1L);

    @Mock
    private CohortAccessService cohortAccessService;

    @Mock
    private PredictionFeatureSnapshotReader featureSnapshotReader;

    @Mock
    private StudyTimePredictionRequestAssembler requestAssembler;

    @Mock
    private PredictionClient predictionClient;

    @Mock
    private Clock clock;

    @InjectMocks
    private StudyTimePredictionService predictionService;

    @Test
    @DisplayName("현재 KST 집계일의 전날까지 피처를 조회하고 예측 요청 정상 처리")
    void readsFeaturesAfterValidatingActiveMembership(CapturedOutput output) {
        PredictionFeatureSnapshot snapshot = emptySnapshot();
        StudyTimePredictionRequest request = emptyRequest();
        StudyTimePredictionResult expected = new StudyTimePredictionResult(
                7.21,
                "study-time-model"
        );
        given(cohortAccessService.requireActiveMembershipId(COHORT_ID, USER_ID))
                .willReturn(COHORT_MEMBERSHIP_ID);
        given(clock.instant()).willReturn(CURRENT_TIME);
        given(featureSnapshotReader.read(
                USER_ID,
                COHORT_ID,
                COHORT_MEMBERSHIP_ID,
                FEATURE_DATE
        ))
                .willReturn(snapshot);
        given(requestAssembler.assemble(snapshot)).willReturn(request);
        given(predictionClient.predict(request, REQUEST_ID)).willReturn(expected);

        StudyTimePredictionResult actual = predictionService.predict(
                USER_ID,
                COHORT_ID,
                REQUEST_ID
        );

        assertSame(expected, actual);
        InOrder inOrder = inOrder(
                cohortAccessService,
                featureSnapshotReader,
                requestAssembler,
                predictionClient
        );
        inOrder.verify(cohortAccessService).requireActiveMembershipId(COHORT_ID, USER_ID);
        inOrder.verify(featureSnapshotReader)
                .read(USER_ID, COHORT_ID, COHORT_MEMBERSHIP_ID, FEATURE_DATE);
        inOrder.verify(requestAssembler).assemble(snapshot);
        inOrder.verify(predictionClient).predict(request, REQUEST_ID);
        assertThat(output.getOut())
                .contains("공부시간 예측을 완료했습니다.")
                .contains("userIdMasked=00000000, cohortId=10, featureDate=1999-12-31")
                .contains("elapsedMs=")
                .contains("modelVersion=study-time-model");
    }

    @Test
    @DisplayName("활성 소속 없음 예외")
    void doesNotReadFeaturesWhenActiveMembershipDoesNotExist(CapturedOutput output) {
        BusinessException expected = new BusinessException(CohortErrorCode.COHORT_NOT_FOUND);
        given(cohortAccessService.requireActiveMembershipId(COHORT_ID, USER_ID))
                .willThrow(expected);

        BusinessException actual = assertThrows(
                BusinessException.class,
                () -> predictionService.predict(USER_ID, COHORT_ID, REQUEST_ID)
        );

        assertSame(expected, actual);
        verifyNoInteractions(featureSnapshotReader, requestAssembler, predictionClient, clock);
        assertThat(output.getOut())
                .doesNotContain("공부시간 예측에 실패했습니다.");
    }

    @Test
    @DisplayName("prediction-service 실패를 사용자 문맥과 함께 기록하고 그대로 전달")
    void rethrowsPredictionClientFailureAfterLoggingContext(CapturedOutput output) {
        PredictionFeatureSnapshot snapshot = emptySnapshot();
        StudyTimePredictionRequest request = emptyRequest();
        PredictionClientException expected = PredictionClientException.timeout(
                new IllegalStateException("timeout")
        );
        given(cohortAccessService.requireActiveMembershipId(COHORT_ID, USER_ID))
                .willReturn(COHORT_MEMBERSHIP_ID);
        given(clock.instant()).willReturn(CURRENT_TIME);
        given(featureSnapshotReader.read(
                USER_ID,
                COHORT_ID,
                COHORT_MEMBERSHIP_ID,
                FEATURE_DATE
        )).willReturn(snapshot);
        given(requestAssembler.assemble(snapshot)).willReturn(request);
        given(predictionClient.predict(request, REQUEST_ID)).willThrow(expected);

        PredictionClientException actual = assertThrows(
                PredictionClientException.class,
                () -> predictionService.predict(USER_ID, COHORT_ID, REQUEST_ID)
        );

        assertSame(expected, actual);
        assertThat(output.getOut())
                .contains("공부시간 예측에 실패했습니다.")
                .contains("userIdMasked=00000000, cohortId=10, featureDate=1999-12-31")
                .contains("reason=TIMEOUT")
                .contains("elapsedMs=")
                .doesNotContain("timeout");
    }

    private PredictionFeatureSnapshot emptySnapshot() {
        return new PredictionFeatureSnapshot(
                FEATURE_DATE,
                FEATURE_DATE,
                "Asia/Seoul",
                new StudyHistory(List.of(), null, 0L, 0L),
                new AttendanceHistory(List.of(), 0L),
                new GamificationHistory(1, 0L, List.of())
        );
    }

    private StudyTimePredictionRequest emptyRequest() {
        return new StudyTimePredictionRequest(
                0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
                0.0, 0.0,
                0.0, 0.0, 0.0, 0.0, 0,
                null, 0.0, 0.0, 0.0,
                null, null,
                1, 0L, 0L, 0.0,
                1, 0, 0, 0, 0, 0, 0,
                0L
        );
    }
}
