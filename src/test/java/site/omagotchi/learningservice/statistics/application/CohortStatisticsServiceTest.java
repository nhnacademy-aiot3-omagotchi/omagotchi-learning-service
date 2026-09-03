package site.omagotchi.learningservice.statistics.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.cohort.application.CohortErrorCode;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;
import site.omagotchi.learningservice.statistics.application.port.CohortStatisticsRepository;
import site.omagotchi.learningservice.statistics.application.port.CohortStatisticsRepository.MemberTodayStudySeconds;
import site.omagotchi.learningservice.statistics.application.result.DailyTotalResult;
import site.omagotchi.learningservice.statistics.application.result.DurationBucketResult;
import site.omagotchi.learningservice.statistics.application.result.TodayResult;
import site.omagotchi.learningservice.statistics.application.result.TrendResult;
import site.omagotchi.learningservice.study.application.StudyRecordAggregationQueryService;
import site.omagotchi.learningservice.study.application.result.MemberCurrentTimerResult;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("기수 학습 통계")
@ExtendWith(MockitoExtension.class)
class CohortStatisticsServiceTest {

    private static final UUID MANAGER_USER_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000001"
    );
    private static final Long COHORT_ID = 10L;
    private static final Instant CALCULATED_AT = Instant.parse("2000-01-07T18:59:59Z");
    private static final LocalDate AGGREGATION_DATE = LocalDate.of(
            2000,
            Month.JANUARY,
            7
    );

    @Mock
    private CohortAccessService cohortAccessService;

    @Mock
    private CohortStatisticsRepository cohortStatisticsRepository;

    @Mock
    private StudyRecordAggregationQueryService studyRecordAggregationQueryService;

    @Mock
    private Clock clock;

    @InjectMocks
    private CohortStatisticsService cohortStatisticsService;

    @Nested
    @DisplayName("오늘 통계 조회")
    class GetToday {

        @Test
        @DisplayName("실행 중 타이머를 합계와 시간 구간에 반영")
        void includesRunningTimerInTodaySummaryAndDurationBuckets() {
            given(clock.instant()).willReturn(CALCULATED_AT);
            List<MemberTodayStudySeconds> confirmedDurations = List.of(
                    new MemberTodayStudySeconds(101L, 0L),
                    new MemberTodayStudySeconds(102L, 1_800L),
                    new MemberTodayStudySeconds(103L, 3_600L),
                    new MemberTodayStudySeconds(104L, 7_200L)
            );
            given(cohortStatisticsRepository.findTodayStudySeconds(
                    COHORT_ID,
                    AGGREGATION_DATE
            )).willReturn(confirmedDurations);
            given(studyRecordAggregationQueryService.getCurrentTimers(
                    List.of(101L, 102L, 103L, 104L),
                    CALCULATED_AT
            )).willReturn(List.of(new MemberCurrentTimerResult(
                    101L,
                    Instant.parse("2000-01-07T17:59:59Z"),
                    3_600L
            )));

            TodayResult result = cohortStatisticsService.getToday(
                    MANAGER_USER_ID,
                    COHORT_ID
            );

            assertAll(
                    () -> assertEquals(AGGREGATION_DATE, result.aggregationDate()),
                    () -> assertEquals(CALCULATED_AT, result.calculatedAt()),
                    () -> assertEquals(16_200L, result.totalStudySeconds()),
                    () -> assertEquals(4L, result.activeStudentCount()),
                    () -> assertEquals(4L, result.participantCount()),
                    () -> assertEquals(0L, result.noRecordStudentCount()),
                    () -> assertEquals(1L, result.runningTimerCount()),
                    () -> assertEquals(4_050L, result.averageParticipantStudySeconds()),
                    () -> assertEquals(
                            List.of(
                                    "NO_RECORD",
                                    "UNDER_ONE_HOUR",
                                    "ONE_TO_TWO_HOURS",
                                    "TWO_TO_FOUR_HOURS",
                                    "FOUR_HOURS_OR_MORE"
                            ),
                            result.durationBuckets().stream()
                                    .map(DurationBucketResult::code)
                                    .toList()
                    ),
                    () -> assertEquals(
                            List.of(0L, 1L, 2L, 1L, 0L),
                            result.durationBuckets().stream()
                                    .map(DurationBucketResult::memberCount)
                                    .toList()
                    )
            );
            InOrder inOrder = inOrder(
                    cohortAccessService,
                    clock,
                    cohortStatisticsRepository,
                    studyRecordAggregationQueryService
            );
            inOrder.verify(cohortAccessService).requireManager(COHORT_ID, MANAGER_USER_ID);
            inOrder.verify(clock).instant();
            inOrder.verify(cohortStatisticsRepository).findTodayStudySeconds(
                    COHORT_ID,
                    AGGREGATION_DATE
            );
            inOrder.verify(studyRecordAggregationQueryService).getCurrentTimers(
                    List.of(101L, 102L, 103L, 104L),
                    CALCULATED_AT
            );
        }

        @Test
        @DisplayName("0초 실행 타이머는 실행 개수에만 반영")
        void countsJustStartedTimerWithoutChangingStudyDuration() {
            given(clock.instant()).willReturn(CALCULATED_AT);
            given(cohortStatisticsRepository.findTodayStudySeconds(
                    COHORT_ID,
                    AGGREGATION_DATE
            )).willReturn(List.of(
                    new MemberTodayStudySeconds(101L, 0L),
                    new MemberTodayStudySeconds(102L, 0L)
            ));
            given(studyRecordAggregationQueryService.getCurrentTimers(
                    List.of(101L, 102L),
                    CALCULATED_AT
            )).willReturn(List.of(new MemberCurrentTimerResult(
                    101L,
                    CALCULATED_AT,
                    0L
            )));

            TodayResult result = cohortStatisticsService.getToday(
                    MANAGER_USER_ID,
                    COHORT_ID
            );

            assertAll(
                    () -> assertEquals(0L, result.averageParticipantStudySeconds()),
                    () -> assertEquals(0L, result.participantCount()),
                    () -> assertEquals(2L, result.noRecordStudentCount()),
                    () -> assertEquals(1L, result.runningTimerCount()),
                    () -> assertEquals(2L, result.durationBuckets().getFirst().memberCount()),
                    () -> assertEquals(
                            result.activeStudentCount(),
                            result.durationBuckets().stream()
                                    .mapToLong(DurationBucketResult::memberCount)
                                    .sum()
                    )
            );
        }

        @Test
        @DisplayName("관리자 권한 없음 예외")
        void rejectsNonManagerBeforeReadingStatistics() {
            willThrow(new BusinessException(CohortErrorCode.COHORT_MANAGER_REQUIRED))
                    .given(cohortAccessService)
                    .requireManager(COHORT_ID, MANAGER_USER_ID);

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> cohortStatisticsService.getToday(
                            MANAGER_USER_ID,
                            COHORT_ID
                    )
            );

            assertEquals(CohortErrorCode.COHORT_MANAGER_REQUIRED, exception.getErrorCode());
            verifyNoInteractions(
                    clock,
                    cohortStatisticsRepository,
                    studyRecordAggregationQueryService
            );
        }
    }

    @Nested
    @DisplayName("기간 추이 조회")
    class GetTrend {

        @Test
        @DisplayName("빈 날짜 포함 정상 처리")
        void returnsFourteenDayTrendIncludingEmptyDates() {
            Instant calculatedAt = Instant.parse("2000-01-14T03:00:00Z");
            LocalDate from = LocalDate.of(2000, Month.JANUARY, 1);
            LocalDate to = LocalDate.of(2000, Month.JANUARY, 14);
            given(clock.instant()).willReturn(calculatedAt);
            given(cohortStatisticsRepository.findDailyStudySeconds(
                    COHORT_ID,
                    from,
                    to
            )).willReturn(List.of(
                    new DailyTotalResult(from, 3_600L),
                    new DailyTotalResult(to, 7_200L)
            ));

            TrendResult result = cohortStatisticsService.getTrend(
                    MANAGER_USER_ID,
                    COHORT_ID,
                    "14d"
            );

            assertAll(
                    () -> assertEquals("14d", result.window()),
                    () -> assertEquals(from, result.from()),
                    () -> assertEquals(to, result.to()),
                    () -> assertEquals(calculatedAt, result.calculatedAt()),
                    () -> assertEquals(10_800L, result.totalStudySeconds()),
                    () -> assertEquals(771L, result.averageDailyStudySeconds()),
                    () -> assertEquals(14, result.dailyTotals().size()),
                    () -> assertEquals(
                            new DailyTotalResult(from, 3_600L),
                            result.dailyTotals().getFirst()
                    ),
                    () -> assertEquals(
                            new DailyTotalResult(from.plusDays(1), 0L),
                            result.dailyTotals().get(1)
                    ),
                    () -> assertEquals(
                            new DailyTotalResult(to, 7_200L),
                            result.dailyTotals().getLast()
                    )
            );
            InOrder inOrder = inOrder(
                    cohortAccessService,
                    clock,
                    cohortStatisticsRepository
            );
            inOrder.verify(cohortAccessService).requireManager(COHORT_ID, MANAGER_USER_ID);
            inOrder.verify(clock).instant();
            inOrder.verify(cohortStatisticsRepository).findDailyStudySeconds(
                    COHORT_ID,
                    from,
                    to
            );
        }

        @ParameterizedTest
        @CsvSource({
                "7d, 2000-01-07T18:59:59Z, 2000-01-01, 2000-01-07, 7",
                "60d, 2000-02-28T19:00:00Z, 2000-01-01, 2000-02-29, 60"
        })
        @DisplayName("기간 하한과 상한 정상 처리")
        void returnsAllDatesForWindowBounds(
                String requestedWindow,
                String calculatedAtValue,
                String fromValue,
                String toValue,
                int expectedDays
        ) {
            Instant calculatedAt = Instant.parse(calculatedAtValue);
            LocalDate from = LocalDate.parse(fromValue);
            LocalDate to = LocalDate.parse(toValue);
            given(clock.instant()).willReturn(calculatedAt);
            given(cohortStatisticsRepository.findDailyStudySeconds(
                    COHORT_ID,
                    from,
                    to
            )).willReturn(List.of());

            TrendResult result = cohortStatisticsService.getTrend(
                    MANAGER_USER_ID,
                    COHORT_ID,
                    requestedWindow
            );

            assertAll(
                    () -> assertEquals(expectedDays, result.dailyTotals().size()),
                    () -> assertEquals(
                            new DailyTotalResult(from, 0L),
                            result.dailyTotals().getFirst()
                    ),
                    () -> assertEquals(
                            new DailyTotalResult(to, 0L),
                            result.dailyTotals().getLast()
                    ),
                    () -> assertEquals(0L, result.totalStudySeconds()),
                    () -> assertEquals(0L, result.averageDailyStudySeconds())
            );
        }

        @Test
        @DisplayName("관리자 권한 없음 예외")
        void rejectsTrendForNonManagerBeforeValidatingWindow() {
            willThrow(new BusinessException(CohortErrorCode.COHORT_MANAGER_REQUIRED))
                    .given(cohortAccessService)
                    .requireManager(COHORT_ID, MANAGER_USER_ID);

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> cohortStatisticsService.getTrend(
                            MANAGER_USER_ID,
                            COHORT_ID,
                            "61d"
                    )
            );

            assertEquals(CohortErrorCode.COHORT_MANAGER_REQUIRED, exception.getErrorCode());
            verifyNoInteractions(clock, cohortStatisticsRepository);
        }

        @Test
        @DisplayName("지원하지 않는 조회 기간 예외")
        void rejectsUnsupportedTrendWindowBeforeReadingTime() {
            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> cohortStatisticsService.getTrend(
                            MANAGER_USER_ID,
                            COHORT_ID,
                            "61d"
                    )
            );

            assertEquals(CommonErrorCode.INVALID_REQUEST, exception.getErrorCode());
            verifyNoInteractions(clock, cohortStatisticsRepository);
        }
    }

}
