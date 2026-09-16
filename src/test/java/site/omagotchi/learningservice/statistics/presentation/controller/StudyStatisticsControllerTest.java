package site.omagotchi.learningservice.statistics.presentation.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static site.omagotchi.learningservice.support.RestDocs.document;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.restdocs.request.ParameterDescriptor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.learningservice.cohort.application.CohortErrorCode;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;
import site.omagotchi.learningservice.global.logging.HttpErrorEventLogger;
import site.omagotchi.learningservice.global.security.TestJwtKeyConfig;
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
import site.omagotchi.learningservice.support.LearningRestDocsTest;

@DisplayName("관리자 학습 통계 API")
@WebMvcTest(controllers = StudyStatisticsController.class)
@LearningRestDocsTest
class StudyStatisticsControllerTest {

    private static final UUID MANAGER_USER_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000001"
    );
    private static final Long COHORT_ID = 10L;

    @MockitoBean
    private CohortStatisticsService cohortStatisticsService;

    @MockitoBean
    private MemberStatisticsService memberStatisticsService;

    @MockitoBean
    private HttpErrorEventLogger errorEventLogger;

    @Autowired
    private MockMvc mockMvc;

    private static String bearerToken() {
        return "Bearer "
                + TestJwtKeyConfig.issue(
                        "https://identity.omagotchi.local",
                        "omagotchi-api",
                        MANAGER_USER_ID.toString(),
                        "USER");
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
                            1L,
                            5_400L,
                            List.of(
                                    new DurationBucketResult("NO_RECORD", 1L),
                                    new DurationBucketResult("UNDER_ONE_HOUR", 1L),
                                    new DurationBucketResult("ONE_TO_TWO_HOURS", 1L),
                                    new DurationBucketResult("TWO_TO_FOUR_HOURS", 1L),
                                    new DurationBucketResult("FOUR_HOURS_OR_MORE", 0L)
                            )
                    ));

            mockMvc.perform(get("/api/v1/cohorts/{cohort-id}/study-statistics/today", COHORT_ID)
                            .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                    .andDo(document(
                            "study-statistics/get-today",
                            pathParameters(cohortIdParameter()),
                            responseFields(
                                    fieldWithPath("aggregationDate")
                                            .type(JsonFieldType.STRING)
                                            .description("집계 기준일"),
                                    fieldWithPath("calculatedAt")
                                            .type(JsonFieldType.STRING)
                                            .description("통계 계산 시각 (ISO-8601)"),
                                    fieldWithPath("totalStudySeconds")
                                            .type(JsonFieldType.NUMBER)
                                            .description("전체 학습 시간 (초)"),
                                    fieldWithPath("activeStudentCount")
                                            .type(JsonFieldType.NUMBER)
                                            .description("활동 학생 수"),
                                    fieldWithPath("participantCount")
                                            .type(JsonFieldType.NUMBER)
                                            .description("학습 참여 학생 수"),
                                    fieldWithPath("noRecordStudentCount")
                                            .type(JsonFieldType.NUMBER)
                                            .description("학습 기록이 없는 학생 수"),
                                    fieldWithPath("runningTimerCount")
                                            .type(JsonFieldType.NUMBER)
                                            .description("실행 중인 타이머 수"),
                                    fieldWithPath("averageParticipantStudySeconds")
                                            .type(JsonFieldType.NUMBER)
                                            .description("참여 학생 평균 학습 시간 (초)"),
                                    fieldWithPath("durationBuckets")
                                            .type(JsonFieldType.ARRAY)
                                            .description("학습 시간 구간별 학생 수"),
                                    fieldWithPath("durationBuckets[].code")
                                            .type(JsonFieldType.STRING)
                                            .description("학습 시간 구간 코드"),
                                    fieldWithPath("durationBuckets[].memberCount")
                                            .type(JsonFieldType.NUMBER)
                                            .description("구간에 속한 학생 수"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.aggregationDate").value("2000-01-07"))
                    .andExpect(jsonPath("$.calculatedAt").value("2000-01-07T18:59:59Z"))
                    .andExpect(jsonPath("$.totalStudySeconds").value(16_200L))
                    .andExpect(jsonPath("$.activeStudentCount").value(4L))
                    .andExpect(jsonPath("$.participantCount").value(3L))
                    .andExpect(jsonPath("$.noRecordStudentCount").value(1L))
                    .andExpect(jsonPath("$.runningTimerCount").value(1L))
                    .andExpect(jsonPath("$.averageParticipantStudySeconds").value(5_400L))
                    .andExpect(jsonPath("$.durationBuckets.length()").value(5))
                    .andExpect(jsonPath("$.durationBuckets[0].code").value("NO_RECORD"))
                    .andExpect(jsonPath("$.durationBuckets[4].code").value("FOUR_HOURS_OR_MORE"))
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

            mockMvc.perform(get("/api/v1/cohorts/{cohort-id}/study-statistics/today", COHORT_ID)
                            .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                    .andExpect(status().isForbidden())
                    .andExpect(
                            jsonPath("$.code")
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

            mockMvc.perform(get("/api/v1/cohorts/{cohort-id}/study-statistics/trend", COHORT_ID)
                            .queryParam("window", "14d")
                            .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                    .andDo(document(
                            "study-statistics/get-trend",
                            pathParameters(cohortIdParameter()),
                            queryParameters(parameterWithName("window").description("조회 기간 (예: 7d, 14d, 30d)")),
                            responseFields(
                                    fieldWithPath("window")
                                            .type(JsonFieldType.STRING)
                                            .description("조회 기간"),
                                    fieldWithPath("from")
                                            .type(JsonFieldType.STRING)
                                            .description("조회 시작일"),
                                    fieldWithPath("to")
                                            .type(JsonFieldType.STRING)
                                            .description("조회 종료일"),
                                    fieldWithPath("calculatedAt")
                                            .type(JsonFieldType.STRING)
                                            .description("통계 계산 시각 (ISO-8601)"),
                                    fieldWithPath("totalStudySeconds")
                                            .type(JsonFieldType.NUMBER)
                                            .description("전체 학습 시간 (초)"),
                                    fieldWithPath("averageDailyStudySeconds")
                                            .type(JsonFieldType.NUMBER)
                                            .description("일 평균 학습 시간 (초)"),
                                    fieldWithPath("dailyTotals")
                                            .type(JsonFieldType.ARRAY)
                                            .description("일자별 학습 시간"),
                                    fieldWithPath("dailyTotals[].aggregationDate")
                                            .type(JsonFieldType.STRING)
                                            .description("집계 기준일"),
                                    fieldWithPath("dailyTotals[].studySeconds")
                                            .type(JsonFieldType.NUMBER)
                                            .description("해당 일자의 학습 시간 (초)"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.window").value("14d"))
                    .andExpect(jsonPath("$.from").value("2000-01-01"))
                    .andExpect(jsonPath("$.to").value("2000-01-14"))
                    .andExpect(jsonPath("$.calculatedAt").value("2000-01-14T03:00:00Z"))
                    .andExpect(jsonPath("$.totalStudySeconds").value(10_800L))
                    .andExpect(jsonPath("$.averageDailyStudySeconds").value(771L))
                    .andExpect(jsonPath("$.dailyTotals.length()").value(14))
                    .andExpect(jsonPath("$.dailyTotals[0].aggregationDate").value("2000-01-01"))
                    .andExpect(jsonPath("$.dailyTotals[0].studySeconds").value(3_600L))
                    .andExpect(jsonPath("$.dailyTotals[1].studySeconds").value(0L))
                    .andExpect(jsonPath("$.dailyTotals[13].aggregationDate").value("2000-01-14"))
                    .andExpect(jsonPath("$.dailyTotals[13].studySeconds").value(7_200L))
                    .andExpect(jsonPath("$.rank").doesNotExist())
                    .andExpect(jsonPath("$.top").doesNotExist())
                    .andExpect(jsonPath("$.teams").doesNotExist());
        }

        @Test
        @DisplayName("조회 기간 누락 예외")
        void rejectsMissingTrendWindow() throws Exception {
            mockMvc.perform(get("/api/v1/cohorts/{cohort-id}/study-statistics/trend", COHORT_ID)
                            .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(CommonErrorCode.INVALID_REQUEST.code()));

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

            mockMvc.perform(get("/api/v1/cohorts/{cohort-id}/study-statistics/trend", COHORT_ID)
                            .queryParam("window", "61d")
                            .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(CommonErrorCode.INVALID_REQUEST.code()));
        }
    }

    @Nested
    @DisplayName("수강생 통계 목록 조회")
    class GetMembers {

        @Test
        @DisplayName("정상 처리")
        void returnsFirstMemberStatisticsPage() throws Exception {
            given(
                            memberStatisticsService.getMembers(
                                    MANAGER_USER_ID,
                                    COHORT_ID,
                                    "30d",
                                    0,
                                    20,
                                    "periodStudySeconds,desc"))
                    .willReturn(
                            new MemberPageResult(
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
                                                    UUID.fromString(
                                                            "00000000-0000-0000-0000-000000000101"),
                                                    "오마",
                                                    3_600L,
                                                    10_800L,
                                                    3L,
                                                    4L,
                                                    Instant.parse("2000-01-29T12:00:00Z"),
                                                    true,
                                                    Instant.parse("2000-01-30T02:30:00Z")),
                                            new MemberSummaryResult(
                                                    102L,
                                                    UUID.fromString(
                                                            "00000000-0000-0000-0000-000000000102"),
                                                    0L,
                                                    0L,
                                                    0L,
                                                    0L,
                                                    null))));

            mockMvc.perform(get("/api/v1/cohorts/{cohort-id}/study-statistics/members", COHORT_ID)
                            .queryParam("window", "30d")
                            .queryParam("page", "0")
                            .queryParam("size", "20")
                            .queryParam("sort", "periodStudySeconds,desc")
                            .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                    .andDo(document(
                            "study-statistics/get-members",
                            pathParameters(cohortIdParameter()),
                            queryParameters(
                                    parameterWithName("window")
                                            .description("조회 기간 (예: 7d, 14d, 30d)"),
                                    parameterWithName("page")
                                            .optional()
                                            .description("페이지 번호 (0부터 시작)"),
                                    parameterWithName("size")
                                            .optional()
                                            .description("페이지 크기"),
                                    parameterWithName("sort")
                                            .optional()
                                            .description("정렬 조건 (예: periodStudySeconds,desc)")),
                            responseFields(memberPageFields())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.window").value("30d"))
                    .andExpect(jsonPath("$.from").value("2000-01-01"))
                    .andExpect(jsonPath("$.to").value("2000-01-30"))
                    .andExpect(jsonPath("$.calculatedAt").value("2000-01-30T03:00:00Z"))
                    .andExpect(jsonPath("$.items.length()").value(2))
                    .andExpect(jsonPath("$.items[0].cohortMembershipId").value(101L))
                    .andExpect(
                            jsonPath("$.items[0].userId")
                                    .value("00000000-0000-0000-0000-000000000101"))
                    .andExpect(jsonPath("$.items[0].nickname").value("오마"))
                    .andExpect(jsonPath("$.items[0].todayStudySeconds").value(3_600L))
                    .andExpect(jsonPath("$.items[0].periodStudySeconds").value(10_800L))
                    .andExpect(jsonPath("$.items[0].activeStudyDays").value(3L))
                    .andExpect(jsonPath("$.items[0].recordCount").value(4L))
                    .andExpect(jsonPath("$.items[0].lastStudiedAt").value("2000-01-29T12:00:00Z"))
                    .andExpect(jsonPath("$.items[0].isRunning").value(true))
                    .andExpect(jsonPath("$.items[0].timerStartedAt").value("2000-01-30T02:30:00Z"))
                    .andExpect(jsonPath("$.items[1].lastStudiedAt").doesNotExist())
                    .andExpect(jsonPath("$.items[1].isRunning").value(false))
                    .andExpect(jsonPath("$.items[1].nickname").doesNotExist())
                    .andExpect(jsonPath("$.items[1].timerStartedAt").doesNotExist())
                    .andExpect(jsonPath("$.items[0].name").doesNotExist())
                    .andExpect(jsonPath("$.items[0].email").doesNotExist())
                    .andExpect(jsonPath("$.search").doesNotExist())
                    .andExpect(jsonPath("$.page.number").value(0))
                    .andExpect(jsonPath("$.page.size").value(20))
                    .andExpect(jsonPath("$.page.totalElements").value(2L))
                    .andExpect(jsonPath("$.page.totalPages").value(1))
                    .andExpect(jsonPath("$.size").doesNotExist())
                    .andExpect(jsonPath("$.totalElements").doesNotExist())
                    .andExpect(jsonPath("$.totalPages").doesNotExist());
        }

        @Test
        @DisplayName("조회 기간 누락 예외")
        void rejectsMissingMemberStatisticsWindow() throws Exception {
            mockMvc.perform(get("/api/v1/cohorts/{cohort-id}/study-statistics/members", COHORT_ID)
                            .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(CommonErrorCode.INVALID_REQUEST.code()));

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

            mockMvc.perform(get("/api/v1/cohorts/{cohort-id}/study-statistics/members", COHORT_ID)
                            .queryParam("window", "30d")
                            .queryParam("page", "-1")
                            .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(CommonErrorCode.INVALID_REQUEST.code()));
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

            mockMvc.perform(get("/api/v1/cohorts/{cohort-id}/study-statistics/members", COHORT_ID)
                            .queryParam("window", "30d")
                            .queryParam("size", String.valueOf(size))
                            .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(CommonErrorCode.INVALID_REQUEST.code()));
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

            mockMvc.perform(get("/api/v1/cohorts/{cohort-id}/study-statistics/members", COHORT_ID)
                            .queryParam("window", "30d")
                            .queryParam("sort", sort)
                            .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(CommonErrorCode.INVALID_REQUEST.code()));
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
                                    "/api/v1/cohorts/{cohort-id}/study-statistics/members/"
                                            + "{cohort-membership-id}/overview",
                                    COHORT_ID,
                                    cohortMembershipId)
                            .queryParam("window", "7d")
                            .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                    .andDo(document(
                            "study-statistics/get-member-overview",
                            pathParameters(
                                    cohortIdParameter(),
                                    parameterWithName("cohort-membership-id")
                                            .description("수강생 멤버십 ID")),
                            queryParameters(parameterWithName("window").description("조회 기간 (예: 7d, 14d, 30d)")),
                            responseFields(
                                    fieldWithPath("cohortMembershipId")
                                            .type(JsonFieldType.NUMBER)
                                            .description("수강생 멤버십 ID"),
                                    fieldWithPath("userId")
                                            .type(JsonFieldType.STRING)
                                            .description("사용자 ID"),
                                    fieldWithPath("window")
                                            .type(JsonFieldType.STRING)
                                            .description("조회 기간"),
                                    fieldWithPath("from")
                                            .type(JsonFieldType.STRING)
                                            .description("조회 시작일"),
                                    fieldWithPath("to")
                                            .type(JsonFieldType.STRING)
                                            .description("조회 종료일"),
                                    fieldWithPath("calculatedAt")
                                            .type(JsonFieldType.STRING)
                                            .description("통계 계산 시각 (ISO-8601)"),
                                    fieldWithPath("totalStudySeconds")
                                            .type(JsonFieldType.NUMBER)
                                            .description("전체 학습 시간 (초)"),
                                    fieldWithPath("averageDailyStudySeconds")
                                            .type(JsonFieldType.NUMBER)
                                            .description("일 평균 학습 시간 (초)"),
                                    fieldWithPath("activeStudyDays")
                                            .type(JsonFieldType.NUMBER)
                                            .description("학습한 일수"),
                                    fieldWithPath("recordCount")
                                            .type(JsonFieldType.NUMBER)
                                            .description("학습 기록 수"),
                                    fieldWithPath("lastStudiedAt")
                                            .type(JsonFieldType.STRING)
                                            .description("마지막 학습 시각 (ISO-8601)"),
                                    fieldWithPath("dailyTotals")
                                            .type(JsonFieldType.ARRAY)
                                            .description("일자별 학습 시간"),
                                    fieldWithPath("dailyTotals[].aggregationDate")
                                            .type(JsonFieldType.STRING)
                                            .description("집계 기준일"),
                                    fieldWithPath("dailyTotals[].studySeconds")
                                            .type(JsonFieldType.NUMBER)
                                            .description("해당 일자의 학습 시간 (초)"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cohortMembershipId").value(101L))
                    .andExpect(jsonPath("$.userId").value("00000000-0000-0000-0000-000000000101"))
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
                    .andExpect(jsonPath("$.dailyTotals[0].aggregationDate").value("2000-01-01"))
                    .andExpect(jsonPath("$.dailyTotals[0].studySeconds").value(3_600L))
                    .andExpect(jsonPath("$.dailyTotals[1].studySeconds").value(0L))
                    .andExpect(jsonPath("$.dailyTotals[6].aggregationDate").value("2000-01-07"))
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
                                    "/api/v1/cohorts/{cohort-id}/study-statistics/members/"
                                            + "{cohort-membership-id}/overview",
                                    COHORT_ID,
                                    cohortMembershipId)
                            .queryParam("window", "7d")
                            .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                    .andExpect(status().isNotFound())
                    .andExpect(
                            jsonPath("$.code")
                                    .value(CohortErrorCode.COHORT_MEMBERSHIP_NOT_FOUND.code()));
        }

        @Test
        @DisplayName("조회 기간 누락 예외")
        void rejectsMemberOverviewWithoutWindow() throws Exception {
            mockMvc.perform(get(
                                    "/api/v1/cohorts/{cohort-id}/study-statistics/members/"
                                            + "{cohort-membership-id}/overview",
                                    COHORT_ID,
                                    101L)
                            .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(CommonErrorCode.INVALID_REQUEST.code()));

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
                                    "/api/v1/cohorts/{cohort-id}/study-statistics/members/"
                                            + "{cohort-membership-id}/records",
                                    COHORT_ID,
                                    cohortMembershipId)
                            .queryParam("date", "2000-01-07")
                            .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                    .andDo(document(
                            "study-statistics/get-member-daily-records",
                            pathParameters(
                                    cohortIdParameter(),
                                    parameterWithName("cohort-membership-id")
                                            .description("수강생 멤버십 ID")),
                            queryParameters(parameterWithName("date").description("집계 기준일 (yyyy-MM-dd)")),
                            responseFields(
                                    fieldWithPath("cohortMembershipId")
                                            .type(JsonFieldType.NUMBER)
                                            .description("수강생 멤버십 ID"),
                                    fieldWithPath("userId")
                                            .type(JsonFieldType.STRING)
                                            .description("사용자 ID"),
                                    fieldWithPath("date")
                                            .type(JsonFieldType.STRING)
                                            .description("집계 기준일"),
                                    fieldWithPath("calculatedAt")
                                            .type(JsonFieldType.STRING)
                                            .description("통계 계산 시각 (ISO-8601)"),
                                    fieldWithPath("totalStudySeconds")
                                            .type(JsonFieldType.NUMBER)
                                            .description("해당 일자의 전체 학습 시간 (초)"),
                                    fieldWithPath("records")
                                            .type(JsonFieldType.ARRAY)
                                            .description("학습 기록 목록"),
                                    fieldWithPath("records[].id")
                                            .type(JsonFieldType.STRING)
                                            .description("학습 기록 ID"),
                                    fieldWithPath("records[].startTime")
                                            .type(JsonFieldType.STRING)
                                            .description("학습 시작 시각 (ISO-8601)"),
                                    fieldWithPath("records[].endTime")
                                            .type(JsonFieldType.STRING)
                                            .description("학습 종료 시각 (ISO-8601)"),
                                    fieldWithPath("records[].studySeconds")
                                            .type(JsonFieldType.NUMBER)
                                            .description("학습 시간 (초)"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cohortMembershipId").value(101L))
                    .andExpect(jsonPath("$.userId").value("00000000-0000-0000-0000-000000000101"))
                    .andExpect(jsonPath("$.date").value("2000-01-07"))
                    .andExpect(jsonPath("$.calculatedAt").value("2000-01-07T03:00:00Z"))
                    .andExpect(jsonPath("$.totalStudySeconds").value(5_400L))
                    .andExpect(jsonPath("$.records.length()").value(2))
                    .andExpect(
                            jsonPath("$.records[0].id")
                                    .value("00000000-0000-0000-0000-000000000201"))
                    .andExpect(jsonPath("$.records[0].startTime").value("2000-01-06T20:00:00Z"))
                    .andExpect(jsonPath("$.records[0].endTime").value("2000-01-06T21:00:00Z"))
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
                                    "/api/v1/cohorts/{cohort-id}/study-statistics/members/"
                                            + "{cohort-membership-id}/records",
                                    COHORT_ID,
                                    101L)
                            .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(CommonErrorCode.INVALID_REQUEST.code()));

            verifyNoInteractions(memberStatisticsService);
        }

        @Test
        @DisplayName("잘못된 집계일 형식 예외")
        void rejectsMalformedMemberDailyRecordsDate() throws Exception {
            mockMvc.perform(get(
                                    "/api/v1/cohorts/{cohort-id}/study-statistics/members/"
                                            + "{cohort-membership-id}/records",
                                    COHORT_ID,
                                    101L)
                            .queryParam("date", "2000-01-XX")
                            .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(CommonErrorCode.INVALID_REQUEST.code()));

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
                                    "/api/v1/cohorts/{cohort-id}/study-statistics/members/"
                                            + "{cohort-membership-id}/records",
                                    COHORT_ID,
                                    101L)
                            .queryParam("date", "2000-01-31")
                            .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(CommonErrorCode.INVALID_REQUEST.code()));
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
                                    "/api/v1/cohorts/{cohort-id}/study-statistics/members/"
                                            + "{cohort-membership-id}/records",
                                    COHORT_ID,
                                    101L)
                            .queryParam("date", "2000-01-07")
                            .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                    .andExpect(status().isNotFound())
                    .andExpect(
                            jsonPath("$.code")
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

    private static ParameterDescriptor cohortIdParameter() {
        return parameterWithName("cohort-id").description("기수 ID");
    }

    private static FieldDescriptor[] memberPageFields() {
        return new FieldDescriptor[] {
            fieldWithPath("window").type(JsonFieldType.STRING).description("조회 기간"),
            fieldWithPath("from").type(JsonFieldType.STRING).description("조회 시작일"),
            fieldWithPath("to").type(JsonFieldType.STRING).description("조회 종료일"),
            fieldWithPath("calculatedAt")
                    .type(JsonFieldType.STRING)
                    .description("통계 계산 시각 (ISO-8601)"),
            fieldWithPath("items").type(JsonFieldType.ARRAY).description("수강생 통계 목록"),
            fieldWithPath("items[].cohortMembershipId")
                    .type(JsonFieldType.NUMBER)
                    .description("수강생 멤버십 ID"),
            fieldWithPath("items[].userId").type(JsonFieldType.STRING).description("사용자 ID"),
            fieldWithPath("items[].nickname")
                    .type(JsonFieldType.STRING)
                    .optional()
                    .description("닉네임"),
            fieldWithPath("items[].todayStudySeconds")
                    .type(JsonFieldType.NUMBER)
                    .description("오늘 학습 시간 (초)"),
            fieldWithPath("items[].periodStudySeconds")
                    .type(JsonFieldType.NUMBER)
                    .description("기간 내 학습 시간 (초)"),
            fieldWithPath("items[].activeStudyDays")
                    .type(JsonFieldType.NUMBER)
                    .description("학습한 일수"),
            fieldWithPath("items[].recordCount").type(JsonFieldType.NUMBER).description("학습 기록 수"),
            fieldWithPath("items[].lastStudiedAt")
                    .type(JsonFieldType.STRING)
                    .optional()
                    .description("마지막 학습 시각 (ISO-8601)"),
            fieldWithPath("items[].isRunning").type(JsonFieldType.BOOLEAN).description("타이머 실행 여부"),
            fieldWithPath("items[].timerStartedAt")
                    .type(JsonFieldType.STRING)
                    .optional()
                    .description("타이머 시작 시각 (ISO-8601)"),
            fieldWithPath("page").type(JsonFieldType.OBJECT).description("페이지 정보"),
            fieldWithPath("page.number").type(JsonFieldType.NUMBER).description("페이지 번호"),
            fieldWithPath("page.size").type(JsonFieldType.NUMBER).description("페이지 크기"),
            fieldWithPath("page.totalElements").type(JsonFieldType.NUMBER).description("전체 항목 수"),
            fieldWithPath("page.totalPages").type(JsonFieldType.NUMBER).description("전체 페이지 수")
        };
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

}
