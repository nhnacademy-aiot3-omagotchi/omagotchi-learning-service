package site.omagotchi.learningservice.statistics.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.cohort.application.CohortErrorCode;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;
import site.omagotchi.learningservice.gamification.application.CharacterGrowthService;
import site.omagotchi.learningservice.gamification.application.result.RepresentativeCharacterResult;
import site.omagotchi.learningservice.statistics.application.port.MemberStatisticsRepository;
import site.omagotchi.learningservice.statistics.application.port.MemberStatisticsRepository.MemberReference;
import site.omagotchi.learningservice.statistics.application.port.MemberStatisticsRepository.PeriodSummary;
import site.omagotchi.learningservice.statistics.application.query.MemberPageQuery;
import site.omagotchi.learningservice.statistics.application.result.*;
import site.omagotchi.learningservice.study.application.StudyRecordAggregationQueryService;
import site.omagotchi.learningservice.study.application.result.MemberCurrentTimerResult;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

@DisplayName("수강생 학습 통계")
@ExtendWith(MockitoExtension.class)
class MemberStatisticsServiceTest {

    private static final UUID MANAGER_USER_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000001"
    );
    private static final Long COHORT_ID = 10L;
    private static final Long COHORT_MEMBERSHIP_ID = 101L;
    private static final UUID STUDENT_USER_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000101"
    );
    private static final Instant CALCULATED_AT = Instant.parse("2000-01-30T03:00:00Z");
    private static final Instant LAST_STUDIED_AT = Instant.parse("2000-01-29T12:00:00Z");
    private static final Instant TIMER_STARTED_AT = Instant.parse("2000-01-30T02:30:00Z");
    private static final String NICKNAME = "오마";
    private static final LocalDate FROM = LocalDate.of(2000, Month.JANUARY, 1);
    private static final LocalDate TO = LocalDate.of(2000, Month.JANUARY, 30);

    @Mock
    private CohortAccessService cohortAccessService;

    @Mock
    private MemberStatisticsRepository memberStatisticsRepository;

    @Mock
    private CharacterGrowthService characterGrowthService;

    @Mock
    private StudyRecordAggregationQueryService studyRecordAggregationQueryService;

    @Mock
    private Clock clock;

    @InjectMocks
    private MemberStatisticsService memberStatisticsService;

    @Nested
    @DisplayName("수강생 목록 조회")
    class GetMembers {

        @Test
        @DisplayName("기본 조건 첫 페이지 정상 처리")
        void returnsFirstPageWithDefaultCondition() {
            MemberPageQuery query =
                    MemberPageQuery.of("30d", null, null, null);
            List<MemberSummaryResult> items = List.of(new MemberSummaryResult(
                    COHORT_MEMBERSHIP_ID,
                    STUDENT_USER_ID,
                    3_600L,
                    10_800L,
                    3L,
                    4L,
                    LAST_STUDIED_AT
            ));
            given(clock.instant()).willReturn(CALCULATED_AT);
            given(memberStatisticsRepository.findActiveStudentStatisticsPage(
                    COHORT_ID,
                    TO,
                    FROM,
                    TO,
                    query
            )).willReturn(items);
            given(memberStatisticsRepository.countActiveStudents(COHORT_ID))
                    .willReturn(41L);
            given(characterGrowthService.findRepresentativeCharacters(
                    List.of(STUDENT_USER_ID)
            )).willReturn(List.of(new RepresentativeCharacterResult(
                    STUDENT_USER_ID,
                    1_001L,
                    NICKNAME
            )));
            given(studyRecordAggregationQueryService.getCurrentTimers(
                    List.of(COHORT_MEMBERSHIP_ID),
                    CALCULATED_AT
            )).willReturn(List.of(new MemberCurrentTimerResult(
                    COHORT_MEMBERSHIP_ID,
                    TIMER_STARTED_AT,
                    1_800L
            )));

            MemberPageResult result = memberStatisticsService.getMembers(
                    MANAGER_USER_ID,
                    COHORT_ID,
                    "30d",
                    null,
                    null,
                    null
            );

            assertAll(
                    () -> assertEquals("30d", result.window()),
                    () -> assertEquals(FROM, result.from()),
                    () -> assertEquals(TO, result.to()),
                    () -> assertEquals(CALCULATED_AT, result.calculatedAt()),
                    () -> assertEquals(0, result.page()),
                    () -> assertEquals(20, result.size()),
                    () -> assertEquals(41L, result.totalElements()),
                    () -> assertEquals(3, result.totalPages()),
                    () -> assertEquals(
                            items.getFirst()
                                    .withNickname(NICKNAME)
                                    .withRunningTimer(TIMER_STARTED_AT),
                            result.items().getFirst()
                    )
            );
            InOrder inOrder = inOrder(
                    cohortAccessService,
                    clock,
                    memberStatisticsRepository,
                    characterGrowthService,
                    studyRecordAggregationQueryService
            );
            inOrder.verify(cohortAccessService).requireManager(COHORT_ID, MANAGER_USER_ID);
            inOrder.verify(clock).instant();
            inOrder.verify(memberStatisticsRepository).countActiveStudents(COHORT_ID);
            inOrder.verify(memberStatisticsRepository)
                    .findActiveStudentStatisticsPage(COHORT_ID, TO, FROM, TO, query);
            inOrder.verify(characterGrowthService).findRepresentativeCharacters(
                    List.of(STUDENT_USER_ID)
            );
            inOrder.verify(studyRecordAggregationQueryService).getCurrentTimers(
                    List.of(COHORT_MEMBERSHIP_ID),
                    CALCULATED_AT
            );
        }

        @Test
        @DisplayName("범위 밖 페이지 빈 목록 정상 처리")
        void returnsEmptyItemsForOutOfRangePage() {
            given(clock.instant()).willReturn(CALCULATED_AT);
            given(memberStatisticsRepository.countActiveStudents(COHORT_ID))
                    .willReturn(41L);

            MemberPageResult result = memberStatisticsService.getMembers(
                    MANAGER_USER_ID,
                    COHORT_ID,
                    "30d",
                    3,
                    20,
                    "periodStudySeconds,desc"
            );

            assertAll(
                    () -> assertEquals(3, result.page()),
                    () -> assertEquals(List.of(), result.items()),
                    () -> assertEquals(41L, result.totalElements()),
                    () -> assertEquals(3, result.totalPages())
            );
            verify(memberStatisticsRepository).countActiveStudents(COHORT_ID);
            verifyNoMoreInteractions(memberStatisticsRepository);
            verifyNoInteractions(characterGrowthService, studyRecordAggregationQueryService);
        }

        @Test
        @DisplayName("활성 수강생 없음 정상 처리")
        void returnsEmptyFirstPageWhenCohortHasNoActiveStudents() {
            given(clock.instant()).willReturn(CALCULATED_AT);
            given(memberStatisticsRepository.countActiveStudents(COHORT_ID))
                    .willReturn(0L);

            MemberPageResult result = memberStatisticsService.getMembers(
                    MANAGER_USER_ID,
                    COHORT_ID,
                    "30d",
                    null,
                    null,
                    null
            );

            assertAll(
                    () -> assertEquals(List.of(), result.items()),
                    () -> assertEquals(0L, result.totalElements()),
                    () -> assertEquals(0, result.totalPages())
            );
            verify(memberStatisticsRepository).countActiveStudents(COHORT_ID);
            verifyNoMoreInteractions(memberStatisticsRepository);
            verifyNoInteractions(characterGrowthService, studyRecordAggregationQueryService);
        }

        @Test
        @DisplayName("관리자 권한 없음 예외")
        void rejectsNonManagerBeforeValidatingCondition() {
            willThrow(new BusinessException(CohortErrorCode.COHORT_MANAGER_REQUIRED))
                    .given(cohortAccessService)
                    .requireManager(COHORT_ID, MANAGER_USER_ID);

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> memberStatisticsService.getMembers(
                            MANAGER_USER_ID,
                            COHORT_ID,
                            "30d",
                            -1,
                            20,
                            "periodStudySeconds,desc"
                    )
            );

            assertEquals(CohortErrorCode.COHORT_MANAGER_REQUIRED, exception.getErrorCode());
            verifyNoInteractions(
                    clock,
                    memberStatisticsRepository,
                    characterGrowthService,
                    studyRecordAggregationQueryService
            );
        }

        @Test
        @DisplayName("잘못된 페이지 요청값 예외")
        void rejectsInvalidPageBeforeReadingTime() {
            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> memberStatisticsService.getMembers(
                            MANAGER_USER_ID,
                            COHORT_ID,
                            "30d",
                            -1,
                            20,
                            "periodStudySeconds,desc"
                    )
            );

            assertEquals(CommonErrorCode.INVALID_REQUEST, exception.getErrorCode());
            verifyNoInteractions(
                    clock,
                    memberStatisticsRepository,
                    characterGrowthService,
                    studyRecordAggregationQueryService
            );
        }
    }

    @Nested
    @DisplayName("수강생 상세 요약 조회")
    class GetOverview {

        @Test
        @DisplayName("7일 빈 날짜 포함 정상 처리")
        void returnsSevenDayOverviewIncludingEmptyDates() {
            LocalDate from = LocalDate.of(2000, Month.JANUARY, 24);
            givenActiveStudent();
            given(clock.instant()).willReturn(CALCULATED_AT);
            given(memberStatisticsRepository.summarizeActiveRecords(
                    COHORT_MEMBERSHIP_ID,
                    from,
                    TO
            )).willReturn(new PeriodSummary(
                    10_800L,
                    2L,
                    3L,
                    LAST_STUDIED_AT
            ));
            given(memberStatisticsRepository.findMemberDailyStudySeconds(
                    COHORT_MEMBERSHIP_ID,
                    from,
                    TO
            )).willReturn(List.of(
                    new DailyTotalResult(from, 3_600L),
                    new DailyTotalResult(TO, 7_200L)
            ));

            MemberOverviewResult result =
                    memberStatisticsService.getOverview(
                            MANAGER_USER_ID,
                            COHORT_ID,
                            COHORT_MEMBERSHIP_ID,
                            "7d"
                    );

            assertAll(
                    () -> assertEquals(COHORT_MEMBERSHIP_ID, result.cohortMembershipId()),
                    () -> assertEquals(STUDENT_USER_ID, result.userId()),
                    () -> assertEquals("7d", result.window()),
                    () -> assertEquals(from, result.from()),
                    () -> assertEquals(TO, result.to()),
                    () -> assertEquals(CALCULATED_AT, result.calculatedAt()),
                    () -> assertEquals(10_800L, result.totalStudySeconds()),
                    () -> assertEquals(1_542L, result.averageDailyStudySeconds()),
                    () -> assertEquals(2L, result.activeStudyDays()),
                    () -> assertEquals(3L, result.recordCount()),
                    () -> assertEquals(LAST_STUDIED_AT, result.lastStudiedAt()),
                    () -> assertEquals(7, result.dailyTotals().size()),
                    () -> assertEquals(
                            new DailyTotalResult(from, 3_600L),
                            result.dailyTotals().getFirst()
                    ),
                    () -> assertEquals(
                            new DailyTotalResult(from.plusDays(1), 0L),
                            result.dailyTotals().get(1)
                    ),
                    () -> assertEquals(
                            new DailyTotalResult(TO, 7_200L),
                            result.dailyTotals().getLast()
                    )
            );
            InOrder inOrder = inOrder(
                    cohortAccessService,
                    memberStatisticsRepository,
                    clock
            );
            inOrder.verify(cohortAccessService).requireManager(COHORT_ID, MANAGER_USER_ID);
            inOrder.verify(memberStatisticsRepository).findActiveStudent(
                    COHORT_ID,
                    COHORT_MEMBERSHIP_ID
            );
            inOrder.verify(clock).instant();
            inOrder.verify(memberStatisticsRepository).summarizeActiveRecords(
                    COHORT_MEMBERSHIP_ID,
                    from,
                    TO
            );
            inOrder.verify(memberStatisticsRepository).findMemberDailyStudySeconds(
                    COHORT_MEMBERSHIP_ID,
                    from,
                    TO
            );
        }

        @Test
        @DisplayName("60일 무기록 정상 처리")
        void returnsSixtyDayOverviewWithoutRecords() {
            LocalDate from = TO.minusDays(59);
            givenActiveStudent();
            given(clock.instant()).willReturn(CALCULATED_AT);
            given(memberStatisticsRepository.summarizeActiveRecords(
                    COHORT_MEMBERSHIP_ID,
                    from,
                    TO
            )).willReturn(new PeriodSummary(0L, 0L, 0L, null));
            given(memberStatisticsRepository.findMemberDailyStudySeconds(
                    COHORT_MEMBERSHIP_ID,
                    from,
                    TO
            )).willReturn(List.of());

            MemberOverviewResult result =
                    memberStatisticsService.getOverview(
                            MANAGER_USER_ID,
                            COHORT_ID,
                            COHORT_MEMBERSHIP_ID,
                            "60d"
                    );

            assertAll(
                    () -> assertEquals("60d", result.window()),
                    () -> assertEquals(from, result.from()),
                    () -> assertEquals(TO, result.to()),
                    () -> assertEquals(0L, result.totalStudySeconds()),
                    () -> assertEquals(0L, result.averageDailyStudySeconds()),
                    () -> assertEquals(0L, result.activeStudyDays()),
                    () -> assertEquals(0L, result.recordCount()),
                    () -> assertNull(result.lastStudiedAt()),
                    () -> assertEquals(60, result.dailyTotals().size()),
                    () -> assertEquals(
                            new DailyTotalResult(from, 0L),
                            result.dailyTotals().getFirst()
                    ),
                    () -> assertEquals(
                            new DailyTotalResult(TO, 0L),
                            result.dailyTotals().getLast()
                    )
            );
        }

        @Test
        @DisplayName("대상 없음 예외")
        void rejectsMissingOverviewTargetBeforeReadingTime() {
            given(memberStatisticsRepository.findActiveStudent(
                    COHORT_ID,
                    COHORT_MEMBERSHIP_ID
            )).willReturn(Optional.empty());

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> memberStatisticsService.getOverview(
                            MANAGER_USER_ID,
                            COHORT_ID,
                            COHORT_MEMBERSHIP_ID,
                            "7d"
                    )
            );

            assertEquals(CohortErrorCode.COHORT_MEMBERSHIP_NOT_FOUND, exception.getErrorCode());
            verify(memberStatisticsRepository).findActiveStudent(
                    COHORT_ID,
                    COHORT_MEMBERSHIP_ID
            );
            verifyNoMoreInteractions(memberStatisticsRepository);
            verifyNoInteractions(clock);
        }

        @Test
        @DisplayName("관리자 권한 없음 예외")
        void rejectsNonManagerOverviewBeforeValidatingWindow() {
            willThrow(new BusinessException(CohortErrorCode.COHORT_MANAGER_REQUIRED))
                    .given(cohortAccessService)
                    .requireManager(COHORT_ID, MANAGER_USER_ID);

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> memberStatisticsService.getOverview(
                            MANAGER_USER_ID,
                            COHORT_ID,
                            COHORT_MEMBERSHIP_ID,
                            "61d"
                    )
            );

            assertEquals(CohortErrorCode.COHORT_MANAGER_REQUIRED, exception.getErrorCode());
            verifyNoInteractions(clock, memberStatisticsRepository);
        }

        @Test
        @DisplayName("지원하지 않는 조회 기간 예외")
        void rejectsInvalidOverviewWindowBeforeReadingTarget() {
            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> memberStatisticsService.getOverview(
                            MANAGER_USER_ID,
                            COHORT_ID,
                            COHORT_MEMBERSHIP_ID,
                            "61d"
                    )
            );

            assertEquals(CommonErrorCode.INVALID_REQUEST, exception.getErrorCode());
            verifyNoInteractions(clock, memberStatisticsRepository);
        }
    }

    @Nested
    @DisplayName("수강생 일별 기록 조회")
    class GetDailyRecords {

        @Test
        @DisplayName("선택 집계일 기록 정상 처리")
        void returnsRecordsOfSelectedAggregationDate() {
            LocalDate date = LocalDate.of(2000, Month.JANUARY, 7);
            List<MemberDailyRecordResult> records = List.of(
                    new MemberDailyRecordResult(
                            UUID.fromString("00000000-0000-0000-0000-000000000201"),
                            Instant.parse("2000-01-06T20:00:00Z"),
                            Instant.parse("2000-01-06T21:00:00Z"),
                            3_600L
                    ),
                    new MemberDailyRecordResult(
                            UUID.fromString("00000000-0000-0000-0000-000000000202"),
                            Instant.parse("2000-01-06T22:00:00Z"),
                            Instant.parse("2000-01-06T22:30:00Z"),
                            1_800L
                    )
            );
            givenActiveStudent();
            given(clock.instant()).willReturn(CALCULATED_AT);
            given(memberStatisticsRepository.findMemberDailyRecords(
                    COHORT_MEMBERSHIP_ID,
                    date
            )).willReturn(records);

            MemberDailyRecordsResult result =
                    memberStatisticsService.getDailyRecords(
                            MANAGER_USER_ID,
                            COHORT_ID,
                            COHORT_MEMBERSHIP_ID,
                            date
                    );

            assertAll(
                    () -> assertEquals(COHORT_MEMBERSHIP_ID, result.cohortMembershipId()),
                    () -> assertEquals(STUDENT_USER_ID, result.userId()),
                    () -> assertEquals(date, result.date()),
                    () -> assertEquals(CALCULATED_AT, result.calculatedAt()),
                    () -> assertEquals(5_400L, result.totalStudySeconds()),
                    () -> assertEquals(records, result.records())
            );
            InOrder inOrder = inOrder(
                    cohortAccessService,
                    memberStatisticsRepository,
                    clock
            );
            inOrder.verify(cohortAccessService).requireManager(COHORT_ID, MANAGER_USER_ID);
            inOrder.verify(clock).instant();
            inOrder.verify(memberStatisticsRepository).findActiveStudent(
                    COHORT_ID,
                    COHORT_MEMBERSHIP_ID
            );
            inOrder.verify(memberStatisticsRepository).findMemberDailyRecords(
                    COHORT_MEMBERSHIP_ID,
                    date
            );
        }

        @Test
        @DisplayName("선택 집계일 무기록 정상 처리")
        void returnsEmptyRecordsOfSelectedAggregationDate() {
            LocalDate date = LocalDate.of(1999, Month.JANUARY, 7);
            givenActiveStudent();
            given(clock.instant()).willReturn(CALCULATED_AT);
            given(memberStatisticsRepository.findMemberDailyRecords(
                    COHORT_MEMBERSHIP_ID,
                    date
            )).willReturn(List.of());

            MemberDailyRecordsResult result =
                    memberStatisticsService.getDailyRecords(
                            MANAGER_USER_ID,
                            COHORT_ID,
                            COHORT_MEMBERSHIP_ID,
                            date
                    );

            assertAll(
                    () -> assertEquals(date, result.date()),
                    () -> assertEquals(0L, result.totalStudySeconds()),
                    () -> assertEquals(List.of(), result.records())
            );
        }

        @Test
        @DisplayName("집계일 누락 예외")
        void rejectsMissingAggregationDateBeforeReadingTarget() {
            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> memberStatisticsService.getDailyRecords(
                            MANAGER_USER_ID,
                            COHORT_ID,
                            COHORT_MEMBERSHIP_ID,
                            null
                    )
            );

            assertEquals(CommonErrorCode.INVALID_REQUEST, exception.getErrorCode());
            verifyNoInteractions(clock, memberStatisticsRepository);
        }

        @Test
        @DisplayName("미래 집계일 예외")
        void rejectsFutureAggregationDateBeforeReadingTarget() {
            LocalDate futureDate = TO.plusDays(1);
            given(clock.instant()).willReturn(CALCULATED_AT);

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> memberStatisticsService.getDailyRecords(
                            MANAGER_USER_ID,
                            COHORT_ID,
                            COHORT_MEMBERSHIP_ID,
                            futureDate
                    )
            );

            assertEquals(CommonErrorCode.INVALID_REQUEST, exception.getErrorCode());
            verifyNoInteractions(memberStatisticsRepository);
        }

        @Test
        @DisplayName("대상 없음 예외")
        void rejectsMissingDailyRecordsTargetAfterValidatingDate() {
            given(clock.instant()).willReturn(CALCULATED_AT);
            given(memberStatisticsRepository.findActiveStudent(
                    COHORT_ID,
                    COHORT_MEMBERSHIP_ID
            )).willReturn(Optional.empty());

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> memberStatisticsService.getDailyRecords(
                            MANAGER_USER_ID,
                            COHORT_ID,
                            COHORT_MEMBERSHIP_ID,
                            LocalDate.of(2000, Month.JANUARY, 7)
                    )
            );

            assertEquals(CohortErrorCode.COHORT_MEMBERSHIP_NOT_FOUND, exception.getErrorCode());
            InOrder inOrder = inOrder(clock, memberStatisticsRepository);
            inOrder.verify(clock).instant();
            inOrder.verify(memberStatisticsRepository).findActiveStudent(
                    COHORT_ID,
                    COHORT_MEMBERSHIP_ID
            );
            verifyNoMoreInteractions(memberStatisticsRepository);
        }

        @Test
        @DisplayName("관리자 권한 없음 예외")
        void rejectsNonManagerDailyRecordsBeforeValidatingDate() {
            willThrow(new BusinessException(CohortErrorCode.COHORT_MANAGER_REQUIRED))
                    .given(cohortAccessService)
                    .requireManager(COHORT_ID, MANAGER_USER_ID);

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> memberStatisticsService.getDailyRecords(
                            MANAGER_USER_ID,
                            COHORT_ID,
                            COHORT_MEMBERSHIP_ID,
                            null
                    )
            );

            assertEquals(CohortErrorCode.COHORT_MANAGER_REQUIRED, exception.getErrorCode());
            verifyNoInteractions(clock, memberStatisticsRepository);
        }
    }

    private void givenActiveStudent() {
        given(memberStatisticsRepository.findActiveStudent(
                COHORT_ID,
                COHORT_MEMBERSHIP_ID
        )).willReturn(Optional.of(new MemberReference(
                COHORT_MEMBERSHIP_ID,
                STUDENT_USER_ID
        )));
    }
}
