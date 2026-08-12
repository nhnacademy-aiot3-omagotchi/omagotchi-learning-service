package site.omagotchi.learningservice.statistics.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.cohort.domain.CohortErrorCode;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;
import site.omagotchi.learningservice.statistics.application.port.MemberStatisticsRepository;
import site.omagotchi.learningservice.statistics.application.port.MemberStatisticsRepository.MemberReference;
import site.omagotchi.learningservice.statistics.application.port.MemberStatisticsRepository.PeriodSummary;
import site.omagotchi.learningservice.statistics.application.query.MemberPageQuery;
import site.omagotchi.learningservice.statistics.application.result.MemberSummaryResult;
import site.omagotchi.learningservice.statistics.application.result.DailyTotalResult;
import site.omagotchi.learningservice.statistics.application.result.MemberDailyRecordResult;
import site.omagotchi.learningservice.statistics.application.result.MemberDailyRecordsResult;
import site.omagotchi.learningservice.statistics.application.result.MemberOverviewResult;
import site.omagotchi.learningservice.statistics.application.result.MemberPageResult;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@DisplayName("관리자 수강생 통계 페이지 조회")
@ExtendWith(MockitoExtension.class)
class MemberStatisticsServiceTest {

    private static final UUID MANAGER_USER_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000001"
    );
    private static final Long COHORT_ID = 10L;
    private static final Instant CALCULATED_AT = Instant.parse("2000-01-30T03:00:00Z");
    private static final LocalDate FROM = LocalDate.of(2000, Month.JANUARY, 1);
    private static final LocalDate TO = LocalDate.of(2000, Month.JANUARY, 30);

    @Mock
    private CohortAccessService cohortAccessService;

    @Mock
    private MemberStatisticsRepository memberStatisticsRepository;

    @Mock
    private Clock clock;

    @InjectMocks
    private MemberStatisticsService memberStatisticsService;

    @Test
    @DisplayName("기본 조건 첫 페이지 메타데이터 정상 처리")
    void returnsFirstPageWithDefaultCondition() {
        MemberPageQuery query =
                MemberPageQuery.of("30d", null, null, null);
        List<MemberSummaryResult> items = List.of(new MemberSummaryResult(
                101L,
                UUID.fromString("00000000-0000-0000-0000-000000000101"),
                3_600L,
                10_800L,
                3L,
                4L,
                Instant.parse("2000-01-29T12:00:00Z")
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
                () -> assertEquals(items, result.items())
        );
        InOrder inOrder = inOrder(
                cohortAccessService,
                clock,
                memberStatisticsRepository
        );
        inOrder.verify(cohortAccessService).requireManager(COHORT_ID, MANAGER_USER_ID);
        inOrder.verify(clock).instant();
        inOrder.verify(memberStatisticsRepository)
                .findActiveStudentStatisticsPage(COHORT_ID, TO, FROM, TO, query);
        inOrder.verify(memberStatisticsRepository).countActiveStudents(COHORT_ID);
    }

    @Test
    @DisplayName("범위 밖 페이지를 빈 목록과 전체 메타데이터로 정상 처리")
    void returnsEmptyItemsForOutOfRangePage() {
        MemberPageQuery query =
                MemberPageQuery.of(
                        "30d",
                        3,
                        20,
                        "periodStudySeconds,desc"
                );
        given(clock.instant()).willReturn(CALCULATED_AT);
        given(memberStatisticsRepository.findActiveStudentStatisticsPage(
                COHORT_ID,
                TO,
                FROM,
                TO,
                query
        )).willReturn(List.of());
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
    }

    @Test
    @DisplayName("활성 수강생이 없으면 빈 첫 페이지 정상 처리")
    void returnsEmptyFirstPageWhenCohortHasNoActiveStudents() {
        MemberPageQuery query =
                MemberPageQuery.of("30d", null, null, null);
        given(clock.instant()).willReturn(CALCULATED_AT);
        given(memberStatisticsRepository.findActiveStudentStatisticsPage(
                COHORT_ID,
                TO,
                FROM,
                TO,
                query
        )).willReturn(List.of());
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
                        null,
                        null,
                        null
                )
        );

        assertEquals(CohortErrorCode.COHORT_MANAGER_REQUIRED, exception.getErrorCode());
        verifyNoInteractions(clock, memberStatisticsRepository);
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
        verifyNoInteractions(clock, memberStatisticsRepository);
    }

    @Test
    @DisplayName("수강생 7일 overview 빈 날짜 포함 정상 처리")
    void returnsSevenDayOverviewIncludingEmptyDates() {
        Long cohortMembershipId = 101L;
        UUID studentUserId = UUID.fromString(
                "00000000-0000-0000-0000-000000000101"
        );
        LocalDate from = LocalDate.of(2000, Month.JANUARY, 24);
        given(memberStatisticsRepository.findActiveStudent(
                COHORT_ID,
                cohortMembershipId
        )).willReturn(Optional.of(new MemberReference(
                cohortMembershipId,
                studentUserId
        )));
        given(clock.instant()).willReturn(CALCULATED_AT);
        given(memberStatisticsRepository.summarizeActiveRecords(
                cohortMembershipId,
                from,
                TO
        )).willReturn(new PeriodSummary(
                10_800L,
                2L,
                3L,
                Instant.parse("2000-01-29T12:00:00Z")
        ));
        given(memberStatisticsRepository.findMemberDailyStudySeconds(
                cohortMembershipId,
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
                        cohortMembershipId,
                        "7d"
                );

        assertAll(
                () -> assertEquals(cohortMembershipId, result.cohortMembershipId()),
                () -> assertEquals(studentUserId, result.userId()),
                () -> assertEquals("7d", result.window()),
                () -> assertEquals(from, result.from()),
                () -> assertEquals(TO, result.to()),
                () -> assertEquals(CALCULATED_AT, result.calculatedAt()),
                () -> assertEquals(10_800L, result.totalStudySeconds()),
                () -> assertEquals(1_542L, result.averageDailyStudySeconds()),
                () -> assertEquals(2L, result.activeStudyDays()),
                () -> assertEquals(3L, result.recordCount()),
                () -> assertEquals(
                        Instant.parse("2000-01-29T12:00:00Z"),
                        result.lastStudiedAt()
                ),
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
                cohortMembershipId
        );
        inOrder.verify(clock).instant();
        inOrder.verify(memberStatisticsRepository).summarizeActiveRecords(
                cohortMembershipId,
                from,
                TO
        );
        inOrder.verify(memberStatisticsRepository).findMemberDailyStudySeconds(
                cohortMembershipId,
                from,
                TO
        );
    }

    @Test
    @DisplayName("수강생 60일 overview 무기록 정상 처리")
    void returnsSixtyDayOverviewWithoutRecords() {
        Long cohortMembershipId = 101L;
        UUID studentUserId = UUID.fromString(
                "00000000-0000-0000-0000-000000000101"
        );
        LocalDate from = TO.minusDays(59);
        given(memberStatisticsRepository.findActiveStudent(
                COHORT_ID,
                cohortMembershipId
        )).willReturn(Optional.of(new MemberReference(
                cohortMembershipId,
                studentUserId
        )));
        given(clock.instant()).willReturn(CALCULATED_AT);
        given(memberStatisticsRepository.summarizeActiveRecords(
                cohortMembershipId,
                from,
                TO
        )).willReturn(new PeriodSummary(0L, 0L, 0L, null));
        given(memberStatisticsRepository.findMemberDailyStudySeconds(
                cohortMembershipId,
                from,
                TO
        )).willReturn(List.of());

        MemberOverviewResult result =
                memberStatisticsService.getOverview(
                        MANAGER_USER_ID,
                        COHORT_ID,
                        cohortMembershipId,
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
    @DisplayName("overview 대상이 같은 기수 활성 수강생이 아니면 예외")
    void rejectsMissingOverviewTargetBeforeReadingTime() {
        Long cohortMembershipId = 101L;
        given(memberStatisticsRepository.findActiveStudent(
                COHORT_ID,
                cohortMembershipId
        )).willReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> memberStatisticsService.getOverview(
                        MANAGER_USER_ID,
                        COHORT_ID,
                        cohortMembershipId,
                        "7d"
                )
        );

        assertEquals(CohortErrorCode.COHORT_MEMBERSHIP_NOT_FOUND, exception.getErrorCode());
        verify(memberStatisticsRepository).findActiveStudent(
                COHORT_ID,
                cohortMembershipId
        );
        verifyNoMoreInteractions(memberStatisticsRepository);
        verifyNoInteractions(clock);
    }

    @Test
    @DisplayName("overview 관리자 권한 없음 예외")
    void rejectsNonManagerOverviewBeforeReadingTarget() {
        Long cohortMembershipId = 101L;
        willThrow(new BusinessException(CohortErrorCode.COHORT_MANAGER_REQUIRED))
                .given(cohortAccessService)
                .requireManager(COHORT_ID, MANAGER_USER_ID);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> memberStatisticsService.getOverview(
                        MANAGER_USER_ID,
                        COHORT_ID,
                        cohortMembershipId,
                        "7d"
                )
        );

        assertEquals(CohortErrorCode.COHORT_MANAGER_REQUIRED, exception.getErrorCode());
        verifyNoInteractions(clock, memberStatisticsRepository);
    }

    @Test
    @DisplayName("수강생 선택 집계일 기록 정상 처리")
    void returnsRecordsOfSelectedAggregationDate() {
        Long cohortMembershipId = 101L;
        UUID studentUserId = UUID.fromString(
                "00000000-0000-0000-0000-000000000101"
        );
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
        given(memberStatisticsRepository.findActiveStudent(
                COHORT_ID,
                cohortMembershipId
        )).willReturn(Optional.of(new MemberReference(
                cohortMembershipId,
                studentUserId
        )));
        given(clock.instant()).willReturn(CALCULATED_AT);
        given(memberStatisticsRepository.findMemberDailyRecords(
                cohortMembershipId,
                date
        )).willReturn(records);

        MemberDailyRecordsResult result =
                memberStatisticsService.getDailyRecords(
                        MANAGER_USER_ID,
                        COHORT_ID,
                        cohortMembershipId,
                        date
                );

        assertAll(
                () -> assertEquals(cohortMembershipId, result.cohortMembershipId()),
                () -> assertEquals(studentUserId, result.userId()),
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
        inOrder.verify(memberStatisticsRepository).findActiveStudent(
                COHORT_ID,
                cohortMembershipId
        );
        inOrder.verify(clock).instant();
        inOrder.verify(memberStatisticsRepository).findMemberDailyRecords(
                cohortMembershipId,
                date
        );
    }

    @Test
    @DisplayName("수강생 선택 집계일 무기록 정상 처리")
    void returnsEmptyRecordsOfSelectedAggregationDate() {
        Long cohortMembershipId = 101L;
        UUID studentUserId = UUID.fromString(
                "00000000-0000-0000-0000-000000000101"
        );
        LocalDate date = LocalDate.of(1999, Month.JANUARY, 7);
        given(memberStatisticsRepository.findActiveStudent(
                COHORT_ID,
                cohortMembershipId
        )).willReturn(Optional.of(new MemberReference(
                cohortMembershipId,
                studentUserId
        )));
        given(clock.instant()).willReturn(CALCULATED_AT);
        given(memberStatisticsRepository.findMemberDailyRecords(
                cohortMembershipId,
                date
        )).willReturn(List.of());

        MemberDailyRecordsResult result =
                memberStatisticsService.getDailyRecords(
                        MANAGER_USER_ID,
                        COHORT_ID,
                        cohortMembershipId,
                        date
                );

        assertAll(
                () -> assertEquals(date, result.date()),
                () -> assertEquals(0L, result.totalStudySeconds()),
                () -> assertEquals(List.of(), result.records())
        );
    }

    @Test
    @DisplayName("수강생 선택 집계일 미래 날짜 예외")
    void rejectsFutureAggregationDate() {
        Long cohortMembershipId = 101L;
        LocalDate futureDate = TO.plusDays(1);
        given(memberStatisticsRepository.findActiveStudent(
                COHORT_ID,
                cohortMembershipId
        )).willReturn(Optional.of(new MemberReference(
                cohortMembershipId,
                UUID.fromString("00000000-0000-0000-0000-000000000101")
        )));
        given(clock.instant()).willReturn(CALCULATED_AT);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> memberStatisticsService.getDailyRecords(
                        MANAGER_USER_ID,
                        COHORT_ID,
                        cohortMembershipId,
                        futureDate
                )
        );

        assertEquals(CommonErrorCode.INVALID_REQUEST, exception.getErrorCode());
        verify(memberStatisticsRepository).findActiveStudent(
                COHORT_ID,
                cohortMembershipId
        );
        verifyNoMoreInteractions(memberStatisticsRepository);
    }

    @Test
    @DisplayName("수강생 선택 집계일 대상 없음 예외")
    void rejectsMissingDailyRecordsTargetBeforeReadingTime() {
        Long cohortMembershipId = 101L;
        given(memberStatisticsRepository.findActiveStudent(
                COHORT_ID,
                cohortMembershipId
        )).willReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> memberStatisticsService.getDailyRecords(
                        MANAGER_USER_ID,
                        COHORT_ID,
                        cohortMembershipId,
                        LocalDate.of(2000, Month.JANUARY, 7)
                )
        );

        assertEquals(CohortErrorCode.COHORT_MEMBERSHIP_NOT_FOUND, exception.getErrorCode());
        verify(memberStatisticsRepository).findActiveStudent(
                COHORT_ID,
                cohortMembershipId
        );
        verifyNoMoreInteractions(memberStatisticsRepository);
        verifyNoInteractions(clock);
    }

    @Test
    @DisplayName("수강생 선택 집계일 관리자 권한 없음 예외")
    void rejectsNonManagerDailyRecordsBeforeReadingTarget() {
        willThrow(new BusinessException(CohortErrorCode.COHORT_MANAGER_REQUIRED))
                .given(cohortAccessService)
                .requireManager(COHORT_ID, MANAGER_USER_ID);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> memberStatisticsService.getDailyRecords(
                        MANAGER_USER_ID,
                        COHORT_ID,
                        101L,
                        LocalDate.of(2000, Month.JANUARY, 7)
                )
        );

        assertEquals(CohortErrorCode.COHORT_MANAGER_REQUIRED, exception.getErrorCode());
        verifyNoInteractions(clock, memberStatisticsRepository);
    }
}
