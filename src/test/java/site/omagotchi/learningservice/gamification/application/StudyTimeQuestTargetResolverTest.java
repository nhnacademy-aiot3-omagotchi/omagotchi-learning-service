package site.omagotchi.learningservice.gamification.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import site.omagotchi.learningservice.gamification.domain.QuestTargetSource;
import site.omagotchi.learningservice.gamification.domain.StudyTimeQuestTarget;
import site.omagotchi.learningservice.prediction.application.StudyTimePredictionService;
import site.omagotchi.learningservice.prediction.application.exception.PredictionClientException;
import site.omagotchi.learningservice.prediction.application.result.StudyTimePredictionResult;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
@DisplayName("학습 시간 퀘스트 목표 산정")
class StudyTimeQuestTargetResolverTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final Long COHORT_ID = 7L;
    private static final LocalDate QUEST_DATE = LocalDate.of(2026, 8, 5);
    private static final int MIN_SECONDS = 12_600; // 3h30m
    private static final int MAX_SECONDS = 41_400; // 11h30m

    @Mock
    private StudyTimePredictionService studyTimePredictionService;

    @Mock
    private UserStudySecondsReader userStudySecondsReader;

    private StudyTimeQuestTargetResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new StudyTimeQuestTargetResolver(
                studyTimePredictionService,
                userStudySecondsReader,
                new StudyTimeQuestProperties(1.1, MIN_SECONDS, MAX_SECONDS)
        );
    }

    @Test
    @DisplayName("예측이 성공하면 계수를 적용한 MODEL 목표를 낸다")
    void usesPredictionWhenAvailable(CapturedOutput output) {
        when(userStudySecondsReader.findActiveCohortId(USER_ID)).thenReturn(Optional.of(COHORT_ID));
        when(userStudySecondsReader.hasStudyRecordBefore(USER_ID, COHORT_ID, QUEST_DATE)).thenReturn(true);
        when(studyTimePredictionService.predict(USER_ID, COHORT_ID, null))
                .thenReturn(new StudyTimePredictionResult(4.0, "study-time-2026-08-16"));

        StudyTimeQuestTarget target = resolver.resolve(USER_ID, QUEST_DATE);

        assertAll(
                () -> assertEquals(15_840, target.targetSeconds()),
                () -> assertEquals(QuestTargetSource.MODEL, target.source()),
                () -> assertEquals("study-time-2026-08-16", target.modelVersion())
        );
        assertThat(output.getOut())
                .contains("학습 시간 퀘스트 목표 산정: ")
                .contains("예측 4.0시간 → 도전계수 1.1배 → 계산 4시간 24분")
                .contains("→ 보정 없음 → 최종 4시간 24분")
                .contains("사용자(userIdMasked)=00000000, 기수(cohortId)=7")
                .contains("계산초(calculatedTargetSeconds)=15840")
                .contains("정책범위(minTargetSeconds/maxTargetSeconds)=12600/41400")
                .contains("보정(adjustment)=보정 없음(NONE), 최종초(targetSeconds)=15840")
                .contains("모델(modelVersion)=study-time-2026-08-16")
                .doesNotContain(USER_ID.toString());
    }

    @Test
    @DisplayName("예측 목표가 하한보다 작으면 최소 목표 적용 과정을 기록한다")
    void logsMinimumClampCalculation(CapturedOutput output) {
        when(userStudySecondsReader.findActiveCohortId(USER_ID)).thenReturn(Optional.of(COHORT_ID));
        when(userStudySecondsReader.hasStudyRecordBefore(USER_ID, COHORT_ID, QUEST_DATE)).thenReturn(true);
        when(studyTimePredictionService.predict(USER_ID, COHORT_ID, null))
                .thenReturn(new StudyTimePredictionResult(2.178, "study-time-2026-08-16"));

        StudyTimeQuestTarget target = resolver.resolve(USER_ID, QUEST_DATE);

        assertEquals(MIN_SECONDS, target.targetSeconds());
        assertThat(output.getOut())
                .contains("예측 2.178시간 → 도전계수 1.1배 → 계산 2시간 23분 45초")
                .contains("→ 최소 목표 3시간 30분 적용 → 최종 3시간 30분")
                .contains("계산초(calculatedTargetSeconds)=8625")
                .contains("보정(adjustment)=최소 목표 3시간 30분 적용(MIN_CLAMP)")
                .contains("최종초(targetSeconds)=12600")
                .doesNotContain(USER_ID.toString());
    }

    @Test
    @DisplayName("예측 목표가 상한보다 크면 최대 목표 적용 과정을 기록한다")
    void logsMaximumClampCalculation(CapturedOutput output) {
        when(userStudySecondsReader.findActiveCohortId(USER_ID)).thenReturn(Optional.of(COHORT_ID));
        when(userStudySecondsReader.hasStudyRecordBefore(USER_ID, COHORT_ID, QUEST_DATE)).thenReturn(true);
        when(studyTimePredictionService.predict(USER_ID, COHORT_ID, null))
                .thenReturn(new StudyTimePredictionResult(11.5, "study-time-2026-08-16"));

        StudyTimeQuestTarget target = resolver.resolve(USER_ID, QUEST_DATE);

        assertEquals(MAX_SECONDS, target.targetSeconds());
        assertThat(output.getOut())
                .contains("예측 11.5시간 → 도전계수 1.1배 → 계산 12시간 39분")
                .contains("→ 최대 목표 11시간 30분 적용 → 최종 11시간 30분")
                .contains("계산초(calculatedTargetSeconds)=45540")
                .contains("보정(adjustment)=최대 목표 11시간 30분 적용(MAX_CLAMP)")
                .contains("최종초(targetSeconds)=41400")
                .doesNotContain(USER_ID.toString());
    }

    @Test
    @DisplayName("예측이 실패하면 최근 7 등원일 평균(B2)으로 폴백한다")
    void fallsBackToRuleWhenPredictionFails(CapturedOutput output) {
        when(userStudySecondsReader.findActiveCohortId(USER_ID)).thenReturn(Optional.of(COHORT_ID));
        when(userStudySecondsReader.hasStudyRecordBefore(USER_ID, COHORT_ID, QUEST_DATE)).thenReturn(true);
        when(studyTimePredictionService.predict(USER_ID, COHORT_ID, null))
                .thenThrow(PredictionClientException.timeout(new IllegalStateException("민감한 장애 상세")));
        when(userStudySecondsReader.recentAttendedAverageSeconds(USER_ID, COHORT_ID, QUEST_DATE))
                .thenReturn(18_000L); // 5h

        StudyTimeQuestTarget target = resolver.resolve(USER_ID, QUEST_DATE);

        assertAll(
                () -> assertEquals(19_800, target.targetSeconds()), // 18000 * 1.1
                () -> assertEquals(QuestTargetSource.RULE_B2, target.source()),
                () -> assertNull(target.modelVersion())
        );
        assertThat(output.getOut())
                .contains("prediction-service 호출 실패로 학습 시간 퀘스트 목표를 규칙으로 폴백합니다.")
                .contains("사용자(userIdMasked)=00000000, 기수(cohortId)=7")
                .contains("실패사유(reason)=시간 초과(TIMEOUT)")
                .contains("규칙 기반 학습 시간 퀘스트 목표 산정:")
                .contains("최근 등원일 평균 5시간 0분 → 도전계수 1.1배")
                .contains("→ 계산 5시간 30분 → 보정 없음 → 최종 5시간 30분")
                .contains("평균초(attendedAverageSeconds)=18000")
                .contains("보정(adjustment)=보정 없음(NONE)")
                .contains("최종초(targetSeconds)=19800")
                .doesNotContain(USER_ID.toString());
    }

    @Test
    @DisplayName("예측이 실패하고 최근 등원 평균도 0이면 하한을 기본 목표로 낸다")
    void fallsBackToDefaultWhenNoRecentAttendance(CapturedOutput output) {
        when(userStudySecondsReader.findActiveCohortId(USER_ID)).thenReturn(Optional.of(COHORT_ID));
        when(userStudySecondsReader.hasStudyRecordBefore(USER_ID, COHORT_ID, QUEST_DATE)).thenReturn(true);
        when(studyTimePredictionService.predict(USER_ID, COHORT_ID, null))
                .thenThrow(new IllegalStateException("콜드스타트"));
        when(userStudySecondsReader.recentAttendedAverageSeconds(USER_ID, COHORT_ID, QUEST_DATE))
                .thenReturn(0L);

        StudyTimeQuestTarget target = resolver.resolve(USER_ID, QUEST_DATE);

        assertAll(
                () -> assertEquals(MIN_SECONDS, target.targetSeconds()),
                () -> assertEquals(QuestTargetSource.DEFAULT, target.source())
        );
        assertThat(output.getOut())
                .contains("예측 목표 산정 중 예상하지 못한 오류가 발생해 규칙으로 폴백합니다.")
                .contains("예외유형(exception)=java.lang.IllegalStateException")
                .contains("최근 등원 학습 이력이 없어 학습 시간 퀘스트 목표를 기본값으로 산정합니다.")
                .contains("기본목표(targetSeconds)=12600초(3시간 30분)")
                .doesNotContain(USER_ID.toString());
    }

    @Test
    @DisplayName("활성 기수 소속이 없으면 예측을 호출하지 않고 기본 목표를 낸다")
    void skipsPredictionWithoutActiveCohort(CapturedOutput output) {
        when(userStudySecondsReader.findActiveCohortId(USER_ID)).thenReturn(Optional.empty());

        StudyTimeQuestTarget target = resolver.resolve(USER_ID, QUEST_DATE);

        assertAll(
                () -> assertEquals(MIN_SECONDS, target.targetSeconds()),
                () -> assertEquals(QuestTargetSource.DEFAULT, target.source())
        );
        assertThat(output.getOut())
                .contains("활성 기수 소속이 없어 학습 시간 퀘스트 목표를 기본값으로 산정합니다.")
                .contains("사용자(userIdMasked)=00000000")
                .contains("퀘스트 날짜(questDate)=2026-08-05")
                .contains("기본목표(targetSeconds)=12600초(3시간 30분)")
                .doesNotContain(USER_ID.toString());
    }

    @Test
    @DisplayName("확정 학습 기록이 없으면 예측을 호출하지 않고 기본 목표를 낸다")
    void skipsPredictionWithoutStudyHistory(CapturedOutput output) {
        // 기록이 0일이면 모델 입력이 전부 0이라 누구에게나 같은 값이 나온다. 그건 예측이 아니다.
        when(userStudySecondsReader.findActiveCohortId(USER_ID)).thenReturn(Optional.of(COHORT_ID));
        when(userStudySecondsReader.hasStudyRecordBefore(USER_ID, COHORT_ID, QUEST_DATE)).thenReturn(false);

        StudyTimeQuestTarget target = resolver.resolve(USER_ID, QUEST_DATE);

        assertAll(
                () -> assertEquals(MIN_SECONDS, target.targetSeconds()),
                () -> assertEquals(QuestTargetSource.DEFAULT, target.source())
        );
        verifyNoInteractions(studyTimePredictionService);
        verify(userStudySecondsReader, never()).recentAttendedAverageSeconds(USER_ID, COHORT_ID, QUEST_DATE);
        assertThat(output.getOut())
                .contains("확정 학습 기록이 없어 학습 시간 퀘스트 목표를 기본값으로 산정합니다.")
                .contains("사용자(userIdMasked)=00000000, 기수(cohortId)=7")
                .contains("퀘스트 날짜(questDate)=2026-08-05")
                .contains("기본목표(targetSeconds)=12600초(3시간 30분)")
                .doesNotContain(USER_ID.toString());
    }

    @Test
    @DisplayName("규칙 평균이 상한을 넘으면 상한으로 자른다")
    void clampsRuleAverageToMaximum(CapturedOutput output) {
        when(userStudySecondsReader.findActiveCohortId(USER_ID)).thenReturn(Optional.of(COHORT_ID));
        when(userStudySecondsReader.hasStudyRecordBefore(USER_ID, COHORT_ID, QUEST_DATE)).thenReturn(true);
        when(studyTimePredictionService.predict(eq(USER_ID), eq(COHORT_ID), any()))
                .thenThrow(new IllegalStateException("장애"));
        when(userStudySecondsReader.recentAttendedAverageSeconds(USER_ID, COHORT_ID, QUEST_DATE))
                .thenReturn(40_000L); // * 1.1 = 44000 > 41400

        StudyTimeQuestTarget target = resolver.resolve(USER_ID, QUEST_DATE);

        assertEquals(MAX_SECONDS, target.targetSeconds());
        assertThat(output.getOut())
                .contains("규칙 기반 학습 시간 퀘스트 목표 산정:")
                .contains("최근 등원일 평균 11시간 6분 40초 → 도전계수 1.1배")
                .contains("→ 계산 12시간 13분 20초")
                .contains("→ 최대 목표 11시간 30분 적용 → 최종 11시간 30분")
                .contains("보정(adjustment)=최대 목표 11시간 30분 적용(MAX_CLAMP)")
                .contains("최종초(targetSeconds)=41400")
                .doesNotContain(USER_ID.toString());
    }

    @Test
    @DisplayName("예측 결과가 비어 있으면 규칙으로 폴백한다")
    void fallsBackToRuleWhenPredictionResultIsEmpty(CapturedOutput output) {
        when(userStudySecondsReader.findActiveCohortId(USER_ID)).thenReturn(Optional.of(COHORT_ID));
        when(userStudySecondsReader.hasStudyRecordBefore(USER_ID, COHORT_ID, QUEST_DATE)).thenReturn(true);
        when(studyTimePredictionService.predict(USER_ID, COHORT_ID, null))
                .thenReturn(new StudyTimePredictionResult(null, "study-time-model"));
        when(userStudySecondsReader.recentAttendedAverageSeconds(USER_ID, COHORT_ID, QUEST_DATE))
                .thenReturn(18_000L);

        StudyTimeQuestTarget target = resolver.resolve(USER_ID, QUEST_DATE);

        assertEquals(QuestTargetSource.RULE_B2, target.source());
        assertThat(output.getOut())
                .contains("prediction-service가 유효한 학습 시간 예측값을 반환하지 않아 규칙으로 폴백합니다.")
                .contains("사용자(userIdMasked)=00000000, 기수(cohortId)=7")
                .doesNotContain(USER_ID.toString());
    }

    @Test
    @DisplayName("규칙 산정 중 오류가 발생하면 기본 목표로 폴백한다")
    void fallsBackToDefaultWhenRuleFails(CapturedOutput output) {
        when(userStudySecondsReader.findActiveCohortId(USER_ID)).thenReturn(Optional.of(COHORT_ID));
        when(userStudySecondsReader.hasStudyRecordBefore(USER_ID, COHORT_ID, QUEST_DATE)).thenReturn(true);
        when(studyTimePredictionService.predict(USER_ID, COHORT_ID, null))
                .thenThrow(new IllegalStateException("예측 오류"));
        when(userStudySecondsReader.recentAttendedAverageSeconds(USER_ID, COHORT_ID, QUEST_DATE))
                .thenThrow(new IllegalStateException("규칙 오류"));

        StudyTimeQuestTarget target = resolver.resolve(USER_ID, QUEST_DATE);

        assertEquals(QuestTargetSource.DEFAULT, target.source());
        assertThat(output.getOut())
                .contains("규칙으로 학습 시간 퀘스트 목표를 산정하지 못해 기본값으로 폴백합니다.")
                .contains("사용자(userIdMasked)=00000000, 기수(cohortId)=7")
                .contains("퀘스트 날짜(questDate)=2026-08-05")
                .contains("예외유형(exception)=java.lang.IllegalStateException")
                .doesNotContain(USER_ID.toString());
    }
}
