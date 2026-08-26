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
import site.omagotchi.learningservice.statistics.application.port.CohortStatisticsRepository.TodaySummary;
import site.omagotchi.learningservice.statistics.application.result.DailyTotalResult;
import site.omagotchi.learningservice.statistics.application.result.DurationBucketResult;
import site.omagotchi.learningservice.statistics.application.result.TodayResult;
import site.omagotchi.learningservice.statistics.application.result.TrendResult;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    private Clock clock;

    @InjectMocks
    private CohortStatisticsService cohortStatisticsService;

    @Nested
    @DisplayName("오늘 통계 조회")
    class GetToday {

        @Test
        @DisplayName("합계와 시간 구간 정상 처리")
        void returnsTodaySummaryAndDurationBuckets() {
            given(clock.instant()).willReturn(CALCULATED_AT);
            given(cohortStatisticsRepository.summarizeToday(COHORT_ID, AGGREGATION_DATE))
                    .willReturn(new TodaySummary(
                            16_200L,
                            4L,
                            3L,
                            1L,
                            durationBuckets(1L, 1L, 1L, 1L, 0L)
                    ));

            TodayResult result = cohortStatisticsService.getToday(
                    MANAGER_USER_ID,
                    COHORT_ID
            );

            assertAll(
                    () -> assertEquals(AGGREGATION_DATE, result.aggregationDate()),
                    () -> assertEquals(CALCULATED_AT, result.calculatedAt()),
                    () -> assertEquals(16_200L, result.totalStudySeconds()),
                    () -> assertEquals(4L, result.activeStudentCount()),
                    () -> assertEquals(3L, result.participantCount()),
                    () -> assertEquals(1L, result.noRecordStudentCount()),
                    () -> assertEquals(5_400L, result.averageParticipantStudySeconds()),
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
                            List.of(1L, 1L, 1L, 1L, 0L),
                            result.durationBuckets().stream()
                                    .map(DurationBucketResult::memberCount)
                                    .toList()
                    )
            );
            InOrder inOrder = inOrder(
                    cohortAccessService,
                    clock,
                    cohortStatisticsRepository
            );
            inOrder.verify(cohortAccessService).requireManager(COHORT_ID, MANAGER_USER_ID);
            inOrder.verify(clock).instant();
            inOrder.verify(cohortStatisticsRepository).summarizeToday(
                    COHORT_ID,
                    AGGREGATION_DATE
            );
        }

        @Test
        @DisplayName("참여자가 없으면 평균 0초 정상 처리")
        void returnsZeroAverageWhenNoStudentParticipates() {
            given(clock.instant()).willReturn(CALCULATED_AT);
            given(cohortStatisticsRepository.summarizeToday(COHORT_ID, AGGREGATION_DATE))
                    .willReturn(new TodaySummary(
                            0L,
                            2L,
                            0L,
                            2L,
                            durationBuckets(2L, 0L, 0L, 0L, 0L)
                    ));

            TodayResult result = cohortStatisticsService.getToday(
                    MANAGER_USER_ID,
                    COHORT_ID
            );

            assertAll(
                    () -> assertEquals(0L, result.averageParticipantStudySeconds()),
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
            verifyNoInteractions(clock, cohortStatisticsRepository);
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

    private List<DurationBucketResult> durationBuckets(
            long noRecord,
            long underOneHour,
            long oneToTwoHours,
            long twoToFourHours,
            long fourHoursOrMore
    ) {
        return List.of(
                new DurationBucketResult("NO_RECORD", noRecord),
                new DurationBucketResult("UNDER_ONE_HOUR", underOneHour),
                new DurationBucketResult("ONE_TO_TWO_HOURS", oneToTwoHours),
                new DurationBucketResult("TWO_TO_FOUR_HOURS", twoToFourHours),
                new DurationBucketResult("FOUR_HOURS_OR_MORE", fourHoursOrMore)
        );
    }
}
