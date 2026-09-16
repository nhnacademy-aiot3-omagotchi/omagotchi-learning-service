package site.omagotchi.learningservice.ranking.presentation;

import static org.hamcrest.Matchers.nullValue;
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
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;
import site.omagotchi.learningservice.global.logging.HttpErrorEventLogger;
import site.omagotchi.learningservice.global.security.TestJwtKeyConfig;
import site.omagotchi.learningservice.ranking.application.StudyRankingQueryService;
import site.omagotchi.learningservice.ranking.application.query.StudyRankingPeriodSelection;
import site.omagotchi.learningservice.ranking.application.query.StudyRankingQuery;
import site.omagotchi.learningservice.ranking.application.result.HistoricalStudyRankingResult;
import site.omagotchi.learningservice.ranking.application.result.MemberStudyRankingViewResult;
import site.omagotchi.learningservice.ranking.application.result.MyStudyRankingResult;
import site.omagotchi.learningservice.ranking.application.result.StudyRankingBoardResult;
import site.omagotchi.learningservice.ranking.application.result.StudyRankingEntryResult;
import site.omagotchi.learningservice.ranking.application.result.TodayStudyRankingResult;
import site.omagotchi.learningservice.support.LearningRestDocsTest;

@DisplayName("학습 랭킹 API")
@WebMvcTest(controllers = MemberStudyRankingController.class)
@LearningRestDocsTest
class StudyRankingControllerTest {

    private static final UUID USER_ID = new UUID(0L, 1L);
    private static final Long COHORT_ID = 10L;
    private static final LocalDate AGGREGATION_DATE = LocalDate.parse("2000-01-13");
    private static final Instant CALCULATED_AT = Instant.parse("2000-01-12T20:00:00Z");

    @MockitoBean
    private StudyRankingQueryService studyRankingQueryService;

    @MockitoBean
    private HttpErrorEventLogger errorEventLogger;

    @Autowired
    private MockMvc mockMvc;

    private static String bearerToken() {
        return "Bearer "
                + TestJwtKeyConfig.issue(
                        "https://identity.omagotchi.local",
                        "omagotchi-api",
                        USER_ID.toString(),
                        "USER");
    }

    @Nested
    @DisplayName("수강생 랭킹 보드 조회")
    class GetMemberRanking {

        @Test
        @DisplayName("오늘 기준 시각과 타이머 상태 정상 응답")
        void returnsTodayBoardWithTimerState() throws Exception {
            MemberStudyRankingViewResult view = memberView(
                    new StudyRankingEntryResult(1L, "첫째", 7_200L, true),
                    new StudyRankingEntryResult(3L, "나", 1_800L, false)
            );
            given(studyRankingQueryService.getTodayMemberView(
                    USER_ID,
                    COHORT_ID,
                    new StudyRankingQuery(2)
            )).willReturn(new TodayStudyRankingResult<>(
                    AGGREGATION_DATE,
                    CALCULATED_AT,
                    view
            ));

            mockMvc.perform(get("/api/v1/cohorts/{cohort-id}/study-rankings/today", COHORT_ID)
                            .queryParam("maxRank", "2")
                            .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                    .andExpect(status().isOk())
                    .andDo(document(
                            "ranking/member-today",
                            pathParameters(parameterWithName("cohort-id").description("조회할 기수 ID")),
                            queryParameters(parameterWithName("maxRank").optional().description("반환할 최대 순위 (생략 시 전체)")),
                            responseFields(memberTodayFields())))
                    .andExpect(jsonPath("$.aggregationDate").value("2000-01-13"))
                    .andExpect(jsonPath("$.calculatedAt").value(CALCULATED_AT.toString()))
                    .andExpect(jsonPath("$.rankedMemberCount").value(3))
                    .andExpect(jsonPath("$.returnedEntryCount").value(1))
                    .andExpect(jsonPath("$.entries[0].rank").value(1))
                    .andExpect(jsonPath("$.entries[0].timerRunning").value(true))
                    .andExpect(jsonPath("$.myRanking.ranking.timerRunning").value(false))
                    .andExpect(jsonPath("$.startDate").doesNotExist())
                    .andExpect(jsonPath("$.includedThroughDate").doesNotExist());
        }

        @Test
        @DisplayName("확정 일간 응답에 타이머 필드를 노출하지 않음")
        void returnsHistoricalDailyBoardWithoutTimerState() throws Exception {
            LocalDate date = LocalDate.parse("2000-01-12");
            MemberStudyRankingViewResult view = memberView(
                    new StudyRankingEntryResult(1L, "첫째", 7_200L),
                    new StudyRankingEntryResult(3L, "나", 1_800L)
            );
            given(studyRankingQueryService.getHistoricalMemberView(
                    USER_ID,
                    COHORT_ID,
                    StudyRankingPeriodSelection.daily(date),
                    new StudyRankingQuery(null)
            )).willReturn(new HistoricalStudyRankingResult<>(
                    date,
                    Optional.of(date),
                    view
            ));

            mockMvc.perform(get(
                                    "/api/v1/cohorts/{cohort-id}/study-rankings/daily/{date}",
                                    COHORT_ID,
                                    date)
                            .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                    .andExpect(status().isOk())
                    .andDo(document(
                            "ranking/member-daily",
                            pathParameters(
                                    parameterWithName("cohort-id").description("조회할 기수 ID"),
                                    parameterWithName("date")
                                            .description("조회할 날짜 (yyyy-MM-dd)")),
                            queryParameters(parameterWithName("maxRank").optional().description("반환할 최대 순위")),
                            responseFields(memberHistoricalFields())))
                    .andExpect(jsonPath("$.startDate").value("2000-01-12"))
                    .andExpect(jsonPath("$.includedThroughDate").value("2000-01-12"))
                    .andExpect(jsonPath("$.entries[0].timerRunning").doesNotExist())
                    .andExpect(jsonPath("$.myRanking.ranking.timerRunning").doesNotExist())
                    .andExpect(jsonPath("$.calculatedAt").doesNotExist());
        }

        @Test
        @DisplayName("확정 기간이 없으면 nullable 종료일 정상 응답")
        void returnsNullIncludedThroughDate() throws Exception {
            LocalDate monday = LocalDate.parse("2000-01-10");
            MemberStudyRankingViewResult emptyView = new MemberStudyRankingViewResult(
                    new StudyRankingBoardResult(0L, List.of()),
                    new MyStudyRankingResult(0L, Optional.empty())
            );
            given(studyRankingQueryService.getHistoricalMemberView(
                    USER_ID,
                    COHORT_ID,
                    StudyRankingPeriodSelection.weekly(monday),
                    new StudyRankingQuery(null)
            )).willReturn(new HistoricalStudyRankingResult<>(
                    monday,
                    Optional.empty(),
                    emptyView
            ));

            mockMvc.perform(get(
                                    "/api/v1/cohorts/{cohort-id}/study-rankings/weekly/{week-start-date}",
                                    COHORT_ID,
                                    monday)
                            .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                    .andExpect(status().isOk())
                    .andDo(document(
                            "ranking/member-weekly-empty",
                            pathParameters(
                                    parameterWithName("cohort-id").description("조회할 기수 ID"),
                                    parameterWithName("week-start-date")
                                            .description("주간 시작일 (월요일, yyyy-MM-dd)")),
                            responseFields(memberHistoricalEmptyFields())))
                    .andExpect(jsonPath("$.includedThroughDate").value(nullValue()))
                    .andExpect(jsonPath("$.entries").isEmpty())
                    .andExpect(jsonPath("$.myRanking.ranked").value(false));
        }

        @Test
        @DisplayName("일간 날짜 형식 오류 요청 거부")
        void rejectsInvalidDailyDateFormat() throws Exception {
            mockMvc.perform(get(
                                    "/api/v1/cohorts/{cohort-id}/study-rankings/daily/{date}",
                                    COHORT_ID,
                                    "not-a-date")
                            .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(CommonErrorCode.INVALID_REQUEST.code()));

            verifyNoInteractions(studyRankingQueryService);
        }
    }

    @Test
    @DisplayName("월간 확정 개인 순위 조회")
    void documentsHistoricalMonthlyRanking() throws Exception {
        // Given: 월간 확정 순위 응답 준비
        YearMonth month = YearMonth.of(2000, 1);
        MemberStudyRankingViewResult view =
                memberView(
                        new StudyRankingEntryResult(1L, "첫째", 7200L),
                        new StudyRankingEntryResult(2L, "나", 1800L));
        given(
                        studyRankingQueryService.getHistoricalMemberView(
                                USER_ID,
                                COHORT_ID,
                                StudyRankingPeriodSelection.monthly(month),
                                new StudyRankingQuery(2)))
                .willReturn(
                        new HistoricalStudyRankingResult<>(
                                month.atDay(1), Optional.of(month.atEndOfMonth()), view));

        // When & Then
        mockMvc.perform(get(
                                "/api/v1/cohorts/{cohort-id}/study-rankings/monthly/{month}",
                                COHORT_ID,
                                month)
                        .queryParam("maxRank", "2")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andDo(document(
                        "ranking/member-monthly",
                        pathParameters(
                                parameterWithName("cohort-id").description("조회할 기수 ID"),
                                parameterWithName("month").description("조회할 월 (yyyy-MM)")),
                        queryParameters(parameterWithName("maxRank").optional().description("반환할 최대 순위")),
                        responseFields(memberHistoricalFields())))
                .andExpect(jsonPath("$.startDate").value("2000-01-01"));
    }

    @Test
    @DisplayName("주간 확정 개인 순위 조회")
    void documentsHistoricalWeeklyRanking() throws Exception {
        // Given: 주간 확정 순위 응답 준비
        LocalDate monday = LocalDate.of(2000, 1, 10);
        MemberStudyRankingViewResult view =
                memberView(
                        new StudyRankingEntryResult(1L, "첫째", 7200L),
                        new StudyRankingEntryResult(2L, "나", 1800L));
        given(
                        studyRankingQueryService.getHistoricalMemberView(
                                USER_ID,
                                COHORT_ID,
                                StudyRankingPeriodSelection.weekly(monday),
                                new StudyRankingQuery(2)))
                .willReturn(
                        new HistoricalStudyRankingResult<>(
                                monday, Optional.of(monday.plusDays(4)), view));

        // When & Then
        mockMvc.perform(get(
                                "/api/v1/cohorts/{cohort-id}/study-rankings/weekly/{week-start-date}",
                                COHORT_ID,
                                monday)
                        .queryParam("maxRank", "2")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andDo(document(
                        "ranking/member-weekly",
                        pathParameters(
                                parameterWithName("cohort-id").description("기수 ID"),
                                parameterWithName("week-start-date").description("주간 시작일")),
                        queryParameters(parameterWithName("maxRank").optional().description("최대 순위")),
                        responseFields(memberHistoricalFields())));
    }

    private FieldDescriptor[] memberTodayFields() {
        return new FieldDescriptor[] {
            fieldWithPath("aggregationDate")
                    .type(JsonFieldType.STRING)
                    .description("현재 집계 기준일 (서비스 시간대 기준)"),
            fieldWithPath("calculatedAt")
                    .type(JsonFieldType.STRING)
                    .description("응답 계산 시각 (ISO-8601 UTC)"),
            fieldWithPath("rankedMemberCount")
                    .type(JsonFieldType.NUMBER)
                    .description("순위가 있는 전체 수강생 수"),
            fieldWithPath("returnedEntryCount")
                    .type(JsonFieldType.NUMBER)
                    .description("`entries`에 포함된 항목 수"),
            fieldWithPath("entries").type(JsonFieldType.ARRAY).description("최대 순위까지의 수강생 순위"),
            fieldWithPath("entries[].rank").type(JsonFieldType.NUMBER).description("순위"),
            fieldWithPath("entries[].displayName").type(JsonFieldType.STRING).description("표시 이름"),
            fieldWithPath("entries[].studySeconds")
                    .type(JsonFieldType.NUMBER)
                    .description("공부 시간 (초)"),
            fieldWithPath("entries[].timerRunning")
                    .type(JsonFieldType.BOOLEAN)
                    .description("현재 타이머 실행 여부"),
            fieldWithPath("entries[].characterType")
                    .type(JsonFieldType.STRING)
                    .optional()
                    .description("대표 캐릭터 유형 (없으면 null)"),
            fieldWithPath("entries[].colorId")
                    .type(JsonFieldType.STRING)
                    .optional()
                    .description("대표 캐릭터 색상 (없으면 null)"),
            fieldWithPath("entries[].attendanceStreakDays")
                    .type(JsonFieldType.NUMBER)
                    .description("평일 연속 출석일"),
            fieldWithPath("myRanking.ranked").type(JsonFieldType.BOOLEAN).description("내 순위 존재 여부"),
            fieldWithPath("myRanking.ranking")
                    .type(JsonFieldType.OBJECT)
                    .optional()
                    .description("내 순위 (없으면 null)"),
            fieldWithPath("myRanking.ranking.rank")
                    .type(JsonFieldType.NUMBER)
                    .optional()
                    .description("내 순위"),
            fieldWithPath("myRanking.ranking.displayName")
                    .type(JsonFieldType.STRING)
                    .optional()
                    .description("내 표시 이름"),
            fieldWithPath("myRanking.ranking.studySeconds")
                    .type(JsonFieldType.NUMBER)
                    .optional()
                    .description("내 공부 시간 (초)"),
            fieldWithPath("myRanking.ranking.timerRunning")
                    .type(JsonFieldType.BOOLEAN)
                    .optional()
                    .description("내 타이머 실행 여부"),
            fieldWithPath("myRanking.ranking.characterType")
                    .type(JsonFieldType.STRING)
                    .optional()
                    .description("내 캐릭터 유형"),
            fieldWithPath("myRanking.ranking.colorId")
                    .type(JsonFieldType.STRING)
                    .optional()
                    .description("내 캐릭터 색상"),
            fieldWithPath("myRanking.ranking.attendanceStreakDays")
                    .type(JsonFieldType.NUMBER)
                    .optional()
                    .description("내 연속 출석일")
        };
    }

    private FieldDescriptor[] memberHistoricalFields() {
        return new FieldDescriptor[] {
            fieldWithPath("startDate").type(JsonFieldType.STRING).description("조회 기간 시작일"),
            fieldWithPath("includedThroughDate")
                    .type(JsonFieldType.STRING)
                    .optional()
                    .description("실제로 포함된 마지막 확정일 (없으면 null)"),
            fieldWithPath("rankedMemberCount")
                    .type(JsonFieldType.NUMBER)
                    .description("순위가 있는 전체 수강생 수"),
            fieldWithPath("returnedEntryCount")
                    .type(JsonFieldType.NUMBER)
                    .description("`entries`에 포함된 항목 수"),
            fieldWithPath("entries").type(JsonFieldType.ARRAY).description("최대 순위까지의 확정 순위"),
            fieldWithPath("entries[].rank").type(JsonFieldType.NUMBER).description("순위"),
            fieldWithPath("entries[].displayName").type(JsonFieldType.STRING).description("표시 이름"),
            fieldWithPath("entries[].studySeconds")
                    .type(JsonFieldType.NUMBER)
                    .description("확정 공부 시간 (초)"),
            fieldWithPath("entries[].characterType")
                    .type(JsonFieldType.STRING)
                    .optional()
                    .description("대표 캐릭터 유형"),
            fieldWithPath("entries[].colorId")
                    .type(JsonFieldType.STRING)
                    .optional()
                    .description("대표 캐릭터 색상"),
            fieldWithPath("entries[].attendanceStreakDays")
                    .type(JsonFieldType.NUMBER)
                    .description("평일 연속 출석일"),
            fieldWithPath("myRanking.ranked").type(JsonFieldType.BOOLEAN).description("내 순위 존재 여부"),
            fieldWithPath("myRanking.ranking")
                    .type(JsonFieldType.OBJECT)
                    .optional()
                    .description("내 순위 (없으면 null)"),
            fieldWithPath("myRanking.ranking.rank")
                    .type(JsonFieldType.NUMBER)
                    .optional()
                    .description("내 순위"),
            fieldWithPath("myRanking.ranking.displayName")
                    .type(JsonFieldType.STRING)
                    .optional()
                    .description("내 표시 이름"),
            fieldWithPath("myRanking.ranking.studySeconds")
                    .type(JsonFieldType.NUMBER)
                    .optional()
                    .description("내 공부 시간 (초)"),
            fieldWithPath("myRanking.ranking.characterType")
                    .type(JsonFieldType.STRING)
                    .optional()
                    .description("내 캐릭터 유형"),
            fieldWithPath("myRanking.ranking.colorId")
                    .type(JsonFieldType.STRING)
                    .optional()
                    .description("내 캐릭터 색상"),
            fieldWithPath("myRanking.ranking.attendanceStreakDays")
                    .type(JsonFieldType.NUMBER)
                    .optional()
                    .description("내 연속 출석일")
        };
    }

    private FieldDescriptor[] memberHistoricalEmptyFields() {
        return new FieldDescriptor[] {
            fieldWithPath("startDate").type(JsonFieldType.STRING).description("조회 기간 시작일"),
            fieldWithPath("includedThroughDate")
                    .type(JsonFieldType.STRING)
                    .optional()
                    .description("확정 기록이 없으면 null"),
            fieldWithPath("rankedMemberCount")
                    .type(JsonFieldType.NUMBER)
                    .description("순위가 있는 전체 수강생 수"),
            fieldWithPath("returnedEntryCount").type(JsonFieldType.NUMBER).description("반환한 수"),
            fieldWithPath("entries").type(JsonFieldType.ARRAY).description("수강생 순위 (없으면 빈 배열)"),
            fieldWithPath("myRanking.ranked").type(JsonFieldType.BOOLEAN).description("내 순위 존재 여부"),
            fieldWithPath("myRanking.ranking")
                    .type(JsonFieldType.OBJECT)
                    .optional()
                    .description("내 순위 (없으면 null)")
        };
    }

    private MemberStudyRankingViewResult memberView(
            StudyRankingEntryResult leader,
            StudyRankingEntryResult mine
    ) {
        return new MemberStudyRankingViewResult(
                new StudyRankingBoardResult(3L, List.of(leader)),
                new MyStudyRankingResult(3L, Optional.of(mine))
        );
    }

}
