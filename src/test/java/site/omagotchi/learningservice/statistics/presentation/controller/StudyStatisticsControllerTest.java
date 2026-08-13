package site.omagotchi.learningservice.statistics.presentation.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.learningservice.cohort.domain.CohortErrorCode;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;
import site.omagotchi.learningservice.global.exception.GlobalExceptionHandler;
import site.omagotchi.learningservice.statistics.application.CohortStatisticsService;
import site.omagotchi.learningservice.statistics.application.MemberStatisticsService;
import site.omagotchi.learningservice.statistics.application.result.DailyTotalResult;
import site.omagotchi.learningservice.statistics.application.result.DurationBucketResult;
import site.omagotchi.learningservice.statistics.application.result.MemberDailyRecordResult;
import site.omagotchi.learningservice.statistics.application.result.MemberDailyRecordsResult;
import site.omagotchi.learningservice.statistics.application.result.MemberOverviewResult;
import site.omagotchi.learningservice.statistics.application.result.MemberPageResult;
import site.omagotchi.learningservice.statistics.application.result.MemberSummaryResult;
import site.omagotchi.learningservice.statistics.application.result.TodayResult;
import site.omagotchi.learningservice.statistics.application.result.TrendResult;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@DisplayName("관리자 학습 통계 API")
@ExtendWith(MockitoExtension.class)
class StudyStatisticsControllerTest {

    private static final UUID MANAGER_USER_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000001"
    );
    private static final Long COHORT_ID = 10L;
    private static final Instant TOKEN_ISSUED_AT = Instant.parse(
            "2000-01-01T00:00:00Z"
    );
    private static final Instant TOKEN_EXPIRES_AT = Instant.parse(
            "2000-01-01T00:05:00Z"
    );

    @Mock
    private CohortStatisticsService cohortStatisticsService;

    @Mock
    private MemberStatisticsService memberStatisticsService;

    @InjectMocks
    private StudyStatisticsController studyStatisticsController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = standaloneSetup(studyStatisticsController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("오늘 통계 조회")
    class GetToday {

        @Test
        @DisplayName("정상 처리")
        void returnsTodayStatistics() throws Exception {
            given(cohortStatisticsService.getToday(MANAGER_USER_ID, COHORT_ID))
                    .willReturn(new TodayResult(
                            LocalDate.of(2000, Month.JANUARY, 7),
                            Instant.parse("2000-01-07T18:59:59Z"),
                            16_200L,
                            4L,
                            3L,
                            1L,
                            5_400L,
                            List.of(
                                    new DurationBucketResult(
                                            "NO_RECORD",
                                            1L
                                    ),
                                    new DurationBucketResult(
                                            "UNDER_ONE_HOUR",
                                            1L
                                    ),
                                    new DurationBucketResult(
                                            "ONE_TO_TWO_HOURS",
                                            1L
                                    ),
                                    new DurationBucketResult(
                                            "TWO_TO_FOUR_HOURS",
                                            1L
                                    ),
                                    new DurationBucketResult(
                                            "FOUR_HOURS_OR_MORE",
                                            0L
                                    )
                            )
                    ));

            mockMvc.perform(get(
                            "/api/v1/cohorts/{cohortId}/study-statistics/today",
                            COHORT_ID
                    ).principal(authentication()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.aggregationDate").value("2000-01-07"))
                    .andExpect(jsonPath("$.calculatedAt").value("2000-01-07T18:59:59Z"))
                    .andExpect(jsonPath("$.totalStudySeconds").value(16_200L))
                    .andExpect(jsonPath("$.activeStudentCount").value(4L))
                    .andExpect(jsonPath("$.participantCount").value(3L))
                    .andExpect(jsonPath("$.noRecordStudentCount").value(1L))
                    .andExpect(jsonPath("$.averageParticipantStudySeconds").value(5_400L))
                    .andExpect(jsonPath("$.durationBuckets.length()").value(5))
                    .andExpect(jsonPath("$.durationBuckets[0].code").value("NO_RECORD"))
                    .andExpect(jsonPath("$.durationBuckets[4].code")
                            .value("FOUR_HOURS_OR_MORE"))
                    .andExpect(jsonPath("$.window").doesNotExist())
                    .andExpect(jsonPath("$.from").doesNotExist())
                    .andExpect(jsonPath("$.to").doesNotExist())
                    .andExpect(jsonPath("$.zoneId").doesNotExist())
                    .andExpect(jsonPath("$.dayStartsAt").doesNotExist());
        }

        @Test
        @DisplayName("관리자 권한 없음 예외")
        void rejectsNonManager() throws Exception {
            given(cohortStatisticsService.getToday(MANAGER_USER_ID, COHORT_ID))
                    .willThrow(new BusinessException(CohortErrorCode.COHORT_MANAGER_REQUIRED));

            mockMvc.perform(get(
                            "/api/v1/cohorts/{cohortId}/study-statistics/today",
                            COHORT_ID
                    ).principal(authentication()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code")
                            .value(CohortErrorCode.COHORT_MANAGER_REQUIRED.code()));
        }
    }

    @Nested
    @DisplayName("기간 추이 조회")
    class GetTrend {

        @Test
        @DisplayName("정상 처리")
        void returnsFourteenDayTrend() throws Exception {
            given(cohortStatisticsService.getTrend(
                    MANAGER_USER_ID,
                    COHORT_ID,
                    "14d"
            )).willReturn(new TrendResult(
                    "14d",
                    LocalDate.of(2000, Month.JANUARY, 1),
                    LocalDate.of(2000, Month.JANUARY, 14),
                    Instant.parse("2000-01-14T03:00:00Z"),
                    10_800L,
                    771L,
                    fourteenDayTotals()
            ));

            mockMvc.perform(get(
                            "/api/v1/cohorts/{cohortId}/study-statistics/trend",
                            COHORT_ID
                    ).queryParam("window", "14d")
                    .principal(authentication()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.window").value("14d"))
                    .andExpect(jsonPath("$.from").value("2000-01-01"))
                    .andExpect(jsonPath("$.to").value("2000-01-14"))
                    .andExpect(jsonPath("$.calculatedAt").value("2000-01-14T03:00:00Z"))
                    .andExpect(jsonPath("$.totalStudySeconds").value(10_800L))
                    .andExpect(jsonPath("$.averageDailyStudySeconds").value(771L))
                    .andExpect(jsonPath("$.dailyTotals.length()").value(14))
                    .andExpect(jsonPath("$.dailyTotals[0].aggregationDate")
                            .value("2000-01-01"))
                    .andExpect(jsonPath("$.dailyTotals[0].studySeconds").value(3_600L))
                    .andExpect(jsonPath("$.dailyTotals[1].studySeconds").value(0L))
                    .andExpect(jsonPath("$.dailyTotals[13].aggregationDate")
                            .value("2000-01-14"))
                    .andExpect(jsonPath("$.dailyTotals[13].studySeconds").value(7_200L))
                    .andExpect(jsonPath("$.rank").doesNotExist())
                    .andExpect(jsonPath("$.top").doesNotExist())
                    .andExpect(jsonPath("$.teams").doesNotExist());
        }

        @Test
        @DisplayName("조회 기간 누락 예외")
        void rejectsMissingTrendWindow() throws Exception {
            mockMvc.perform(get(
                            "/api/v1/cohorts/{cohortId}/study-statistics/trend",
                            COHORT_ID
                    ).principal(authentication()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code")
                            .value(CommonErrorCode.INVALID_REQUEST.code()));

            verifyNoInteractions(cohortStatisticsService);
        }

        @Test
        @DisplayName("조회 기간 범위 초과 예외")
        void rejectsUnsupportedTrendWindow() throws Exception {
            given(cohortStatisticsService.getTrend(
                    MANAGER_USER_ID,
                    COHORT_ID,
                    "61d"
            )).willThrow(new BusinessException(CommonErrorCode.INVALID_REQUEST));

            mockMvc.perform(get(
                            "/api/v1/cohorts/{cohortId}/study-statistics/trend",
                            COHORT_ID
                    ).queryParam("window", "61d")
                    .principal(authentication()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code")
                            .value(CommonErrorCode.INVALID_REQUEST.code()));
        }
    }

    @Nested
    @DisplayName("수강생 통계 목록 조회")
    class GetMembers {

        @Test
        @DisplayName("정상 처리")
        void returnsFirstMemberStatisticsPage() throws Exception {
            given(memberStatisticsService.getMembers(
                    MANAGER_USER_ID,
                    COHORT_ID,
                    "30d",
                    null,
                    null,
                    null
            )).willReturn(new MemberPageResult(
                    "30d",
                    LocalDate.of(2000, Month.JANUARY, 1),
                    LocalDate.of(2000, Month.JANUARY, 30),
                    Instant.parse("2000-01-30T03:00:00Z"),
                    0,
                    20,
                    2L,
                    1,
                    List.of(
                            new MemberSummaryResult(
                                    101L,
                                    UUID.fromString("00000000-0000-0000-0000-000000000101"),
                                    3_600L,
                                    10_800L,
                                    3L,
                                    4L,
                                    Instant.parse("2000-01-29T12:00:00Z")
                            ),
                            new MemberSummaryResult(
                                    102L,
                                    UUID.fromString("00000000-0000-0000-0000-000000000102"),
                                    0L,
                                    0L,
                                    0L,
                                    0L,
                                    null
                            )
                    )
            ));

            mockMvc.perform(get(
                            "/api/v1/cohorts/{cohortId}/study-statistics/members",
                            COHORT_ID
                    ).queryParam("window", "30d")
                    .principal(authentication()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.window").value("30d"))
                    .andExpect(jsonPath("$.from").value("2000-01-01"))
                    .andExpect(jsonPath("$.to").value("2000-01-30"))
                    .andExpect(jsonPath("$.calculatedAt").value("2000-01-30T03:00:00Z"))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20))
                    .andExpect(jsonPath("$.totalElements").value(2L))
                    .andExpect(jsonPath("$.totalPages").value(1))
                    .andExpect(jsonPath("$.items.length()").value(2))
                    .andExpect(jsonPath("$.items[0].cohortMembershipId").value(101L))
                    .andExpect(jsonPath("$.items[0].userId")
                            .value("00000000-0000-0000-0000-000000000101"))
                    .andExpect(jsonPath("$.items[0].todayStudySeconds").value(3_600L))
                    .andExpect(jsonPath("$.items[0].periodStudySeconds").value(10_800L))
                    .andExpect(jsonPath("$.items[0].activeStudyDays").value(3L))
                    .andExpect(jsonPath("$.items[0].recordCount").value(4L))
                    .andExpect(jsonPath("$.items[0].lastStudiedAt")
                            .value("2000-01-29T12:00:00Z"))
                    .andExpect(jsonPath("$.items[1].lastStudiedAt").doesNotExist())
                    .andExpect(jsonPath("$.items[0].name").doesNotExist())
                    .andExpect(jsonPath("$.items[0].email").doesNotExist())
                    .andExpect(jsonPath("$.search").doesNotExist());
        }

        @Test
        @DisplayName("조회 기간 누락 예외")
        void rejectsMissingMemberStatisticsWindow() throws Exception {
            mockMvc.perform(get(
                            "/api/v1/cohorts/{cohortId}/study-statistics/members",
                            COHORT_ID
                    ).principal(authentication()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code")
                            .value(CommonErrorCode.INVALID_REQUEST.code()));

            verifyNoInteractions(memberStatisticsService);
        }

        @Test
        @DisplayName("음수 페이지 예외")
        void rejectsNegativeMemberStatisticsPage() throws Exception {
            given(memberStatisticsService.getMembers(
                    MANAGER_USER_ID,
                    COHORT_ID,
                    "30d",
                    -1,
                    null,
                    null
            )).willThrow(new BusinessException(CommonErrorCode.INVALID_REQUEST));

            mockMvc.perform(get(
                            "/api/v1/cohorts/{cohortId}/study-statistics/members",
                            COHORT_ID
                    ).queryParam("window", "30d")
                    .queryParam("page", "-1")
                    .principal(authentication()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code")
                            .value(CommonErrorCode.INVALID_REQUEST.code()));
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 101})
        @DisplayName("페이지 크기 범위 초과 예외")
        void rejectsUnsupportedMemberStatisticsSize(int size) throws Exception {
            given(memberStatisticsService.getMembers(
                    MANAGER_USER_ID,
                    COHORT_ID,
                    "30d",
                    null,
                    size,
                    null
            )).willThrow(new BusinessException(CommonErrorCode.INVALID_REQUEST));

            mockMvc.perform(get(
                            "/api/v1/cohorts/{cohortId}/study-statistics/members",
                            COHORT_ID
                    ).queryParam("window", "30d")
                    .queryParam("size", String.valueOf(size))
                    .principal(authentication()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code")
                            .value(CommonErrorCode.INVALID_REQUEST.code()));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "unknown,asc",
                "periodStudySeconds,ASC",
                "periodStudySeconds,desc,extra"
        })
        @DisplayName("정렬 조건 형식 예외")
        void rejectsUnsupportedMemberStatisticsSort(String sort) throws Exception {
            given(memberStatisticsService.getMembers(
                    MANAGER_USER_ID,
                    COHORT_ID,
                    "30d",
                    null,
                    null,
                    sort
            )).willThrow(new BusinessException(CommonErrorCode.INVALID_REQUEST));

            mockMvc.perform(get(
                            "/api/v1/cohorts/{cohortId}/study-statistics/members",
                            COHORT_ID
                    ).queryParam("window", "30d")
                    .queryParam("sort", sort)
                    .principal(authentication()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code")
                            .value(CommonErrorCode.INVALID_REQUEST.code()));
        }
    }

    @Nested
    @DisplayName("수강생 상세 통계 조회")
    class GetMemberOverview {

        @Test
        @DisplayName("정상 처리")
        void returnsSevenDayMemberOverview() throws Exception {
            Long cohortMembershipId = 101L;
            given(memberStatisticsService.getOverview(
                    MANAGER_USER_ID,
                    COHORT_ID,
                    cohortMembershipId,
                    "7d"
            )).willReturn(new MemberOverviewResult(
                    cohortMembershipId,
                    UUID.fromString("00000000-0000-0000-0000-000000000101"),
                    "7d",
                    LocalDate.of(2000, Month.JANUARY, 1),
                    LocalDate.of(2000, Month.JANUARY, 7),
                    Instant.parse("2000-01-07T03:00:00Z"),
                    10_800L,
                    1_542L,
                    2L,
                    3L,
                    Instant.parse("2000-01-07T01:00:00Z"),
                    sevenDayTotals()
            ));

            mockMvc.perform(get(
                            "/api/v1/cohorts/{cohortId}/study-statistics/members/"
                                    + "{cohortMembershipId}/overview",
                            COHORT_ID,
                            cohortMembershipId
                    ).queryParam("window", "7d")
                    .principal(authentication()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cohortMembershipId").value(101L))
                    .andExpect(jsonPath("$.userId")
                            .value("00000000-0000-0000-0000-000000000101"))
                    .andExpect(jsonPath("$.window").value("7d"))
                    .andExpect(jsonPath("$.from").value("2000-01-01"))
                    .andExpect(jsonPath("$.to").value("2000-01-07"))
                    .andExpect(jsonPath("$.calculatedAt").value("2000-01-07T03:00:00Z"))
                    .andExpect(jsonPath("$.totalStudySeconds").value(10_800L))
                    .andExpect(jsonPath("$.averageDailyStudySeconds").value(1_542L))
                    .andExpect(jsonPath("$.activeStudyDays").value(2L))
                    .andExpect(jsonPath("$.recordCount").value(3L))
                    .andExpect(jsonPath("$.lastStudiedAt").value("2000-01-07T01:00:00Z"))
                    .andExpect(jsonPath("$.dailyTotals.length()").value(7))
                    .andExpect(jsonPath("$.dailyTotals[0].aggregationDate")
                            .value("2000-01-01"))
                    .andExpect(jsonPath("$.dailyTotals[0].studySeconds").value(3_600L))
                    .andExpect(jsonPath("$.dailyTotals[1].studySeconds").value(0L))
                    .andExpect(jsonPath("$.dailyTotals[6].aggregationDate")
                            .value("2000-01-07"))
                    .andExpect(jsonPath("$.dailyTotals[6].studySeconds").value(7_200L))
                    .andExpect(jsonPath("$.records").doesNotExist());
        }

        @Test
        @DisplayName("대상 없음 예외")
        void returnsNotFoundForMissingMemberOverviewTarget() throws Exception {
            Long cohortMembershipId = 101L;
            given(memberStatisticsService.getOverview(
                    MANAGER_USER_ID,
                    COHORT_ID,
                    cohortMembershipId,
                    "7d"
            )).willThrow(new BusinessException(CohortErrorCode.COHORT_MEMBERSHIP_NOT_FOUND));

            mockMvc.perform(get(
                            "/api/v1/cohorts/{cohortId}/study-statistics/members/"
                                    + "{cohortMembershipId}/overview",
                            COHORT_ID,
                            cohortMembershipId
                    ).queryParam("window", "7d")
                    .principal(authentication()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code")
                            .value(CohortErrorCode.COHORT_MEMBERSHIP_NOT_FOUND.code()));
        }

        @Test
        @DisplayName("조회 기간 누락 예외")
        void rejectsMemberOverviewWithoutWindow() throws Exception {
            mockMvc.perform(get(
                            "/api/v1/cohorts/{cohortId}/study-statistics/members/"
                                    + "{cohortMembershipId}/overview",
                            COHORT_ID,
                            101L
                    ).principal(authentication()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code")
                            .value(CommonErrorCode.INVALID_REQUEST.code()));

            verifyNoInteractions(memberStatisticsService);
        }
    }

    @Nested
    @DisplayName("수강생 일별 기록 조회")
    class GetMemberDailyRecords {

        @Test
        @DisplayName("정상 처리")
        void returnsMemberRecordsOfSelectedAggregationDate() throws Exception {
            Long cohortMembershipId = 101L;
            LocalDate date = LocalDate.of(2000, Month.JANUARY, 7);
            given(memberStatisticsService.getDailyRecords(
                    MANAGER_USER_ID,
                    COHORT_ID,
                    cohortMembershipId,
                    date
            )).willReturn(new MemberDailyRecordsResult(
                    cohortMembershipId,
                    UUID.fromString("00000000-0000-0000-0000-000000000101"),
                    date,
                    Instant.parse("2000-01-07T03:00:00Z"),
                    5_400L,
                    List.of(
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
                    )
            ));

            mockMvc.perform(get(
                            "/api/v1/cohorts/{cohortId}/study-statistics/members/"
                                    + "{cohortMembershipId}/records",
                            COHORT_ID,
                            cohortMembershipId
                    ).queryParam("date", "2000-01-07")
                    .principal(authentication()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cohortMembershipId").value(101L))
                    .andExpect(jsonPath("$.userId")
                            .value("00000000-0000-0000-0000-000000000101"))
                    .andExpect(jsonPath("$.date").value("2000-01-07"))
                    .andExpect(jsonPath("$.calculatedAt").value("2000-01-07T03:00:00Z"))
                    .andExpect(jsonPath("$.totalStudySeconds").value(5_400L))
                    .andExpect(jsonPath("$.records.length()").value(2))
                    .andExpect(jsonPath("$.records[0].id")
                            .value("00000000-0000-0000-0000-000000000201"))
                    .andExpect(jsonPath("$.records[0].startTime")
                            .value("2000-01-06T20:00:00Z"))
                    .andExpect(jsonPath("$.records[0].endTime")
                            .value("2000-01-06T21:00:00Z"))
                    .andExpect(jsonPath("$.records[0].studySeconds").value(3_600L))
                    .andExpect(jsonPath("$.records[0].aggregationDate").doesNotExist())
                    .andExpect(jsonPath("$.records[0].version").doesNotExist())
                    .andExpect(jsonPath("$.records[0].createdAt").doesNotExist())
                    .andExpect(jsonPath("$.records[0].updatedAt").doesNotExist());
        }

        @Test
        @DisplayName("집계일 누락 예외")
        void rejectsMemberDailyRecordsWithoutDate() throws Exception {
            mockMvc.perform(get(
                            "/api/v1/cohorts/{cohortId}/study-statistics/members/"
                                    + "{cohortMembershipId}/records",
                            COHORT_ID,
                            101L
                    ).principal(authentication()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code")
                            .value(CommonErrorCode.INVALID_REQUEST.code()));

            verifyNoInteractions(memberStatisticsService);
        }

        @Test
        @DisplayName("잘못된 집계일 형식 예외")
        void rejectsMalformedMemberDailyRecordsDate() throws Exception {
            mockMvc.perform(get(
                            "/api/v1/cohorts/{cohortId}/study-statistics/members/"
                                    + "{cohortMembershipId}/records",
                            COHORT_ID,
                            101L
                    ).queryParam("date", "2000-01-XX")
                    .principal(authentication()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code")
                            .value(CommonErrorCode.INVALID_REQUEST.code()));

            verifyNoInteractions(memberStatisticsService);
        }

        @Test
        @DisplayName("미래 집계일 예외")
        void rejectsFutureMemberDailyRecordsDate() throws Exception {
            given(memberStatisticsService.getDailyRecords(
                    MANAGER_USER_ID,
                    COHORT_ID,
                    101L,
                    LocalDate.of(2000, Month.JANUARY, 31)
            )).willThrow(new BusinessException(CommonErrorCode.INVALID_REQUEST));

            mockMvc.perform(get(
                            "/api/v1/cohorts/{cohortId}/study-statistics/members/"
                                    + "{cohortMembershipId}/records",
                            COHORT_ID,
                            101L
                    ).queryParam("date", "2000-01-31")
                    .principal(authentication()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code")
                            .value(CommonErrorCode.INVALID_REQUEST.code()));
        }

        @Test
        @DisplayName("대상 없음 예외")
        void returnsNotFoundForMissingMemberDailyRecordsTarget() throws Exception {
            given(memberStatisticsService.getDailyRecords(
                    MANAGER_USER_ID,
                    COHORT_ID,
                    101L,
                    LocalDate.of(2000, Month.JANUARY, 7)
            )).willThrow(new BusinessException(CohortErrorCode.COHORT_MEMBERSHIP_NOT_FOUND));

            mockMvc.perform(get(
                            "/api/v1/cohorts/{cohortId}/study-statistics/members/"
                                    + "{cohortMembershipId}/records",
                            COHORT_ID,
                            101L
                    ).queryParam("date", "2000-01-07")
                    .principal(authentication()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code")
                            .value(CohortErrorCode.COHORT_MEMBERSHIP_NOT_FOUND.code()));
        }
    }

    private List<DailyTotalResult> sevenDayTotals() {
        return IntStream.rangeClosed(1, 7)
                .mapToObj(day -> new DailyTotalResult(
                        LocalDate.of(2000, Month.JANUARY, day),
                        switch (day) {
                            case 1 -> 3_600L;
                            case 7 -> 7_200L;
                            default -> 0L;
                        }
                ))
                .toList();
    }

    private List<DailyTotalResult> fourteenDayTotals() {
        return IntStream.rangeClosed(1, 14)
                .mapToObj(day -> new DailyTotalResult(
                        LocalDate.of(2000, Month.JANUARY, day),
                        switch (day) {
                            case 1 -> 3_600L;
                            case 14 -> 7_200L;
                            default -> 0L;
                        }
                ))
                .toList();
    }

    private JwtAuthenticationToken authentication() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject(MANAGER_USER_ID.toString())
                .claim("role", "USER")
                .issuedAt(TOKEN_ISSUED_AT)
                .expiresAt(TOKEN_EXPIRES_AT)
                .build();
        return new JwtAuthenticationToken(jwt);
    }
}
