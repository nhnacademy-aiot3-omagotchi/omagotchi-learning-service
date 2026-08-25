package site.omagotchi.learningservice.study.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.cohort.application.CohortErrorCode;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;
import site.omagotchi.learningservice.gamification.application.DailyQuestService;
import site.omagotchi.learningservice.study.application.port.StudyRecordQueryRepository;
import site.omagotchi.learningservice.study.application.result.DailyStudyRecordsResult;
import site.omagotchi.learningservice.study.application.result.DailyStudySecondsResult;
import site.omagotchi.learningservice.study.application.result.MonthlyStudySecondsResult;
import site.omagotchi.learningservice.study.application.result.StudyRecordResult;
import site.omagotchi.learningservice.study.domain.StudyRecord;

import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@DisplayName("학습 기록 조회")
@ExtendWith(MockitoExtension.class)
class StudyRecordQueryServiceTest {

    private static final Long COHORT_ID = 10L;
    private static final Long COHORT_MEMBERSHIP_ID = 1L;
    private static final UUID USER_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000001"
    );
    private static final UUID STUDY_RECORD_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000004"
    );
    private static final LocalDate BASE_DATE = LocalDate.of(2000, Month.JANUARY, 1);
    private static final Instant START_TIME = Instant.parse("2000-01-01T01:00:00Z");
    private static final Instant END_TIME = Instant.parse("2000-01-01T02:00:00Z");
    private static final Instant JANUARY_15_CURRENT_TIME = Instant.parse("2000-01-15T00:00:00Z");

    @Mock
    private StudyRecordQueryRepository studyRecordQueryRepository;

    @Mock
    private CohortAccessService cohortAccessService;

    @Mock
    private DailyQuestService dailyQuestService;

    @Mock
    private Clock clock;

    @InjectMocks
    private StudyRecordQueryService studyRecordQueryService;

    @BeforeEach
    void setUpActiveMembership() {
        given(cohortAccessService.requireActiveMembershipId(COHORT_ID, USER_ID))
                .willReturn(COHORT_MEMBERSHIP_ID);
    }

    @Test
    @DisplayName("정상 처리")
    void returnsStudyRecordResult() {
        UUID studyRecordId = STUDY_RECORD_ID;
        StudyRecord entity = StudyRecord.create(
                COHORT_MEMBERSHIP_ID,
                START_TIME,
                END_TIME,
                3_600L
        );
        given(studyRecordQueryRepository.findActiveByIdAndCohortMembershipId(
                studyRecordId,
                COHORT_MEMBERSHIP_ID
        ))
                .willReturn(Optional.of(entity));

        StudyRecordResult result = studyRecordQueryService.getRecord(
                USER_ID,
                COHORT_ID,
                studyRecordId
        );

        assertAll(
                () -> assertEquals(entity.getId(), result.id()),
                () -> assertEquals(BASE_DATE, result.aggregationDate()),
                () -> assertEquals(START_TIME, result.startTime()),
                () -> assertEquals(END_TIME, result.endTime()),
                () -> assertEquals(3_600L, result.studySeconds())
        );
        verify(cohortAccessService).requireActiveMembershipId(COHORT_ID, USER_ID);
        verify(studyRecordQueryRepository)
                .findActiveByIdAndCohortMembershipId(studyRecordId, COHORT_MEMBERSHIP_ID);
        verifyNoMoreInteractions(studyRecordQueryRepository);
    }

    @Test
    @DisplayName("대상 없음 예외")
    void throwsNotFoundWhenRecordDoesNotExist() {
        UUID studyRecordId = STUDY_RECORD_ID;
        given(studyRecordQueryRepository.findActiveByIdAndCohortMembershipId(
                studyRecordId,
                COHORT_MEMBERSHIP_ID
        ))
                .willReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> studyRecordQueryService.getRecord(USER_ID, COHORT_ID, studyRecordId)
        );

        assertSame(StudyRecordErrorCode.NOT_FOUND, exception.getErrorCode());
        verify(cohortAccessService).requireActiveMembershipId(COHORT_ID, USER_ID);
        verify(studyRecordQueryRepository)
                .findActiveByIdAndCohortMembershipId(studyRecordId, COHORT_MEMBERSHIP_ID);
        verifyNoMoreInteractions(studyRecordQueryRepository);
    }

    @Test
    @DisplayName("활성 소속 없음 예외")
    void doesNotQueryRecordWhenActiveMembershipDoesNotExist() {
        UUID studyRecordId = STUDY_RECORD_ID;
        given(cohortAccessService.requireActiveMembershipId(COHORT_ID, USER_ID))
                .willThrow(new BusinessException(CohortErrorCode.COHORT_NOT_FOUND));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> studyRecordQueryService.getRecord(USER_ID, COHORT_ID, studyRecordId)
        );

        assertSame(CohortErrorCode.COHORT_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(studyRecordQueryRepository);
    }

    @Test
    @DisplayName("일간 활성 기록 전체와 합계 조회")
    void returnsDailyRecordsAndTotalStudySeconds() {
        LocalDate aggregationDate = LocalDate.of(2000, Month.JANUARY, 10);
        StudyRecord first = craeteStudyRecord(
                "2000-01-09T20:00:00Z",
                "2000-01-09T21:00:00Z"
        );
        StudyRecord second = craeteStudyRecord(
                "2000-01-09T22:00:00Z",
                "2000-01-10T00:00:00Z"
        );
        given(clock.instant()).willReturn(JANUARY_15_CURRENT_TIME);
        given(studyRecordQueryRepository.findDailyRecords(
                COHORT_MEMBERSHIP_ID,
                aggregationDate
        )).willReturn(List.of(first, second));

        DailyStudyRecordsResult result = studyRecordQueryService.getDailyRecords(
                USER_ID,
                COHORT_ID,
                aggregationDate
        );

        assertAll(
                () -> assertEquals(aggregationDate, result.aggregationDate()),
                () -> assertEquals(10_800L, result.totalStudySeconds()),
                () -> assertEquals(2, result.records().size()),
                () -> assertEquals(first.getStartTime(), result.records().getFirst().startTime()),
                () -> assertEquals(second.getStartTime(), result.records().getLast().startTime())
        );
        verifyNoInteractions(dailyQuestService);
    }

    @Test
    @DisplayName("일간 기록이 없으면 빈 목록과 0초 반환")
    void returnsEmptyDailyResultWhenNoRecordExists() {
        LocalDate aggregationDate = LocalDate.of(2000, Month.JANUARY, 10);
        given(clock.instant()).willReturn(JANUARY_15_CURRENT_TIME);
        given(studyRecordQueryRepository.findDailyRecords(
                COHORT_MEMBERSHIP_ID,
                aggregationDate
        )).willReturn(List.of());

        DailyStudyRecordsResult result = studyRecordQueryService.getDailyRecords(
                USER_ID,
                COHORT_ID,
                aggregationDate
        );

        assertAll(
                () -> assertEquals(0L, result.totalStudySeconds()),
                () -> assertTrue(result.records().isEmpty())
        );
        verifyNoInteractions(dailyQuestService);
    }

    @Test
    @DisplayName("오늘 일간 기록을 조회하면 학습 돌아보기 퀘스트 진행")
    void progressesRoutineReviewQuestWhenCurrentDailyRecordsAreViewed() {
        LocalDate currentAggregationDate = LocalDate.of(2000, Month.JANUARY, 15);
        given(clock.instant()).willReturn(JANUARY_15_CURRENT_TIME);
        given(studyRecordQueryRepository.findDailyRecords(
                COHORT_MEMBERSHIP_ID,
                currentAggregationDate
        )).willReturn(List.of());

        studyRecordQueryService.getDailyRecords(
                USER_ID,
                COHORT_ID,
                currentAggregationDate
        );

        verify(dailyQuestService).handleRoutineReviewed(USER_ID);
    }

    @Test
    @DisplayName("서버 기준 미래 집계일 조회 거절")
    void rejectsFutureDailyPeriod() {
        LocalDate currentAggregationDate = LocalDate.of(2000, Month.JANUARY, 15);
        given(clock.instant()).willReturn(JANUARY_15_CURRENT_TIME);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> studyRecordQueryService.getDailyRecords(
                        USER_ID,
                        COHORT_ID,
                        currentAggregationDate.plusDays(1)
                )
        );

        assertSame(CommonErrorCode.INVALID_REQUEST, exception.getErrorCode());
        verifyNoInteractions(studyRecordQueryRepository);
        verifyNoInteractions(dailyQuestService);
    }

    @Test
    @DisplayName("월간 희소 집계를 전체 날짜로 0초 보정")
    void fillsMissingAndFutureDatesInMonthlyResult() {
        YearMonth aggregationMonth = YearMonth.of(2000, Month.JANUARY);
        LocalDate currentAggregationDate = LocalDate.of(2000, Month.JANUARY, 15);
        LocalDate startDate = aggregationMonth.atDay(1);
        given(clock.instant()).willReturn(JANUARY_15_CURRENT_TIME);
        given(studyRecordQueryRepository.findDailyStudySeconds(
                COHORT_MEMBERSHIP_ID,
                startDate,
                currentAggregationDate
        )).willReturn(List.of(
                new DailyStudySecondsResult(LocalDate.of(2000, Month.JANUARY, 1), 3_600L),
                new DailyStudySecondsResult(LocalDate.of(2000, Month.JANUARY, 3), 7_200L)
        ));

        MonthlyStudySecondsResult result = studyRecordQueryService.getMonthlyStudySeconds(
                USER_ID,
                COHORT_ID,
                aggregationMonth
        );

        assertAll(
                () -> assertEquals(aggregationMonth, result.aggregationMonth()),
                () -> assertEquals(10_800L, result.totalStudySeconds()),
                () -> assertEquals(31, result.dailyTotals().size()),
                () -> assertEquals(3_600L, result.dailyTotals().get(0).studySeconds()),
                () -> assertEquals(0L, result.dailyTotals().get(1).studySeconds()),
                () -> assertEquals(7_200L, result.dailyTotals().get(2).studySeconds()),
                () -> assertEquals(0L, result.dailyTotals().getLast().studySeconds())
        );
    }

    @Test
    @DisplayName("윤년 무기록 월은 29개 날짜와 0초 반환")
    void returnsAllLeapMonthDatesWhenNoRecordExists() {
        YearMonth aggregationMonth = YearMonth.of(2000, Month.FEBRUARY);
        LocalDate startDate = aggregationMonth.atDay(1);
        LocalDate endDate = aggregationMonth.atEndOfMonth();
        given(clock.instant()).willReturn(Instant.parse("2000-03-01T00:00:00Z"));
        given(studyRecordQueryRepository.findDailyStudySeconds(
                COHORT_MEMBERSHIP_ID,
                startDate,
                endDate
        )).willReturn(List.of());

        MonthlyStudySecondsResult result = studyRecordQueryService.getMonthlyStudySeconds(
                USER_ID,
                COHORT_ID,
                aggregationMonth
        );

        assertAll(
                () -> assertEquals(29, result.dailyTotals().size()),
                () -> assertEquals(LocalDate.of(2000, Month.FEBRUARY, 29),
                        result.dailyTotals().getLast().aggregationDate()),
                () -> assertEquals(0L, result.totalStudySeconds())
        );
    }

    @Test
    @DisplayName("서버 기준 미래 집계월 조회 거절")
    void rejectsFutureMonthlyPeriod() {
        given(clock.instant()).willReturn(JANUARY_15_CURRENT_TIME);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> studyRecordQueryService.getMonthlyStudySeconds(
                        USER_ID,
                        COHORT_ID,
                        YearMonth.of(2000, Month.FEBRUARY)
                )
        );

        assertSame(CommonErrorCode.INVALID_REQUEST, exception.getErrorCode());
        verifyNoInteractions(studyRecordQueryRepository);
    }

    private StudyRecord craeteStudyRecord(
            String startTime,
            String endTime
    ) {
        Instant startInstant = Instant.parse(startTime);
        Instant endInstant = Instant.parse(endTime);
        return StudyRecord.create(
                COHORT_MEMBERSHIP_ID,
                startInstant,
                endInstant,
                endInstant.getEpochSecond() - startInstant.getEpochSecond()
        );
    }
}
