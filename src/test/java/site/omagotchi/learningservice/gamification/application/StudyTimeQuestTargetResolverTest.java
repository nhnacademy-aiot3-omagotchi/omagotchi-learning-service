package site.omagotchi.learningservice.gamification.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.gamification.domain.QuestTargetSource;
import site.omagotchi.learningservice.gamification.domain.StudyTimeQuestTarget;
import site.omagotchi.learningservice.prediction.application.StudyTimePredictionService;
import site.omagotchi.learningservice.prediction.application.result.StudyTimePredictionResult;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
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
    void usesPredictionWhenAvailable() {
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
    }

    @Test
    @DisplayName("예측이 실패하면 최근 7 등원일 평균(B2)으로 폴백한다")
    void fallsBackToRuleWhenPredictionFails() {
        when(userStudySecondsReader.findActiveCohortId(USER_ID)).thenReturn(Optional.of(COHORT_ID));
        when(userStudySecondsReader.hasStudyRecordBefore(USER_ID, COHORT_ID, QUEST_DATE)).thenReturn(true);
        when(studyTimePredictionService.predict(USER_ID, COHORT_ID, null))
                .thenThrow(new IllegalStateException("prediction-service 장애"));
        when(userStudySecondsReader.recentAttendedAverageSeconds(USER_ID, COHORT_ID, QUEST_DATE))
                .thenReturn(18_000L); // 5h

        StudyTimeQuestTarget target = resolver.resolve(USER_ID, QUEST_DATE);

        assertAll(
                () -> assertEquals(19_800, target.targetSeconds()), // 18000 * 1.1
                () -> assertEquals(QuestTargetSource.RULE_B2, target.source()),
                () -> assertNull(target.modelVersion())
        );
    }

    @Test
    @DisplayName("예측이 실패하고 최근 등원 평균도 0이면 하한을 기본 목표로 낸다")
    void fallsBackToDefaultWhenNoRecentAttendance() {
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
    }

    @Test
    @DisplayName("활성 기수 소속이 없으면 예측을 호출하지 않고 기본 목표를 낸다")
    void skipsPredictionWithoutActiveCohort() {
        when(userStudySecondsReader.findActiveCohortId(USER_ID)).thenReturn(Optional.empty());

        StudyTimeQuestTarget target = resolver.resolve(USER_ID, QUEST_DATE);

        assertAll(
                () -> assertEquals(MIN_SECONDS, target.targetSeconds()),
                () -> assertEquals(QuestTargetSource.DEFAULT, target.source())
        );
    }

    @Test
    @DisplayName("확정 학습 기록이 없으면 예측을 호출하지 않고 기본 목표를 낸다")
    void skipsPredictionWithoutStudyHistory() {
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
    }

    @Test
    @DisplayName("규칙 평균이 상한을 넘으면 상한으로 자른다")
    void clampsRuleAverageToMaximum() {
        when(userStudySecondsReader.findActiveCohortId(USER_ID)).thenReturn(Optional.of(COHORT_ID));
        when(userStudySecondsReader.hasStudyRecordBefore(USER_ID, COHORT_ID, QUEST_DATE)).thenReturn(true);
        when(studyTimePredictionService.predict(eq(USER_ID), eq(COHORT_ID), any()))
                .thenThrow(new IllegalStateException("장애"));
        when(userStudySecondsReader.recentAttendedAverageSeconds(USER_ID, COHORT_ID, QUEST_DATE))
                .thenReturn(40_000L); // * 1.1 = 44000 > 41400

        StudyTimeQuestTarget target = resolver.resolve(USER_ID, QUEST_DATE);

        assertEquals(MAX_SECONDS, target.targetSeconds());
    }
}
