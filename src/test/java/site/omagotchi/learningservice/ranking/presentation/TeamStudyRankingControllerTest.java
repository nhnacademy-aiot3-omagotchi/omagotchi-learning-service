package site.omagotchi.learningservice.ranking.presentation;

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
import site.omagotchi.learningservice.ranking.application.TeamStudyRankingQueryService;
import site.omagotchi.learningservice.ranking.application.query.StudyRankingPeriodSelection;
import site.omagotchi.learningservice.ranking.application.query.StudyRankingQuery;
import site.omagotchi.learningservice.ranking.application.result.HistoricalStudyRankingResult;
import site.omagotchi.learningservice.ranking.application.result.MemberStudyRankingViewResult;
import site.omagotchi.learningservice.ranking.application.result.MyStudyRankingResult;
import site.omagotchi.learningservice.ranking.application.result.MyTeamStudyRankingResult;
import site.omagotchi.learningservice.ranking.application.result.StudyRankingBoardResult;
import site.omagotchi.learningservice.ranking.application.result.StudyRankingEntryResult;
import site.omagotchi.learningservice.ranking.application.result.TeamStudyRankingBoardResult;
import site.omagotchi.learningservice.ranking.application.result.TeamStudyRankingEntryResult;
import site.omagotchi.learningservice.ranking.application.result.TeamStudyRankingViewResult;
import site.omagotchi.learningservice.ranking.application.result.TodayStudyRankingResult;
import site.omagotchi.learningservice.support.LearningRestDocsTest;

@DisplayName("팀 학습 랭킹 API")
@WebMvcTest(
        controllers = {TeamMemberStudyRankingController.class, TeamStudyRankingController.class})
@LearningRestDocsTest
class TeamStudyRankingControllerTest {

    private static final UUID USER_ID = new UUID(0L, 1L);
    private static final Long COHORT_ID = 10L;
    private static final Long TEAM_ID = 100L;
    private static final LocalDate AGGREGATION_DATE = LocalDate.parse("2000-01-13");
    private static final Instant CALCULATED_AT = Instant.parse("2000-01-12T20:00:00Z");

    @MockitoBean
    private StudyRankingQueryService studyRankingQueryService;

    @MockitoBean
    private TeamStudyRankingQueryService teamStudyRankingQueryService;

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

    @Test
    @DisplayName("팀원 일간 순위 조회")
    void documentsTeamMemberDailyRanking() throws Exception {
        // Given: 팀원 일간 순위 응답 준비
        LocalDate date = LocalDate.of(2000, 1, 12);
        MemberStudyRankingViewResult view =
                new MemberStudyRankingViewResult(
                        new StudyRankingBoardResult(
                                1L, List.of(new StudyRankingEntryResult(1L, "첫째", 7200L))),
                        new MyStudyRankingResult(1L, Optional.empty()));
        given(
                        studyRankingQueryService.getHistoricalTeamMemberView(
                                USER_ID,
                                COHORT_ID,
                                TEAM_ID,
                                StudyRankingPeriodSelection.daily(date),
                                new StudyRankingQuery(1)))
                .willReturn(new HistoricalStudyRankingResult<>(date, Optional.of(date), view));

        // When & Then
        mockMvc.perform(get(
                                "/api/v1/cohorts/{cohort-id}/teams/{team-id}/study-rankings/daily/{date}",
                                COHORT_ID,
                                TEAM_ID,
                                date)
                        .queryParam("maxRank", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andDo(document(
                        "ranking/team-member-daily",
                        pathParameters(
                                parameterWithName("cohort-id").description("기수 ID"),
                                parameterWithName("team-id").description("팀 ID"),
                                parameterWithName("date").description("날짜 (yyyy-MM-dd)")),
                        queryParameters(parameterWithName("maxRank").optional().description("최대 순위")),
                        responseFields(memberHistoricalFields())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("팀원 주간 순위 조회")
    void documentsTeamMemberWeeklyRanking() throws Exception {
        // Given: 팀원 주간 순위 응답 준비
        LocalDate date = LocalDate.of(2000, 1, 10);
        MemberStudyRankingViewResult view =
                new MemberStudyRankingViewResult(
                        new StudyRankingBoardResult(
                                1L, List.of(new StudyRankingEntryResult(1L, "첫째", 7200L))),
                        new MyStudyRankingResult(1L, Optional.empty()));
        given(
                        studyRankingQueryService.getHistoricalTeamMemberView(
                                USER_ID,
                                COHORT_ID,
                                TEAM_ID,
                                StudyRankingPeriodSelection.weekly(date),
                                new StudyRankingQuery(null)))
                .willReturn(
                        new HistoricalStudyRankingResult<>(
                                date, Optional.of(date.plusDays(4)), view));

        // When & Then
        mockMvc.perform(get(
                                "/api/v1/cohorts/{cohort-id}/teams/{team-id}/study-rankings/weekly/{week-start-date}",
                                COHORT_ID,
                                TEAM_ID,
                                date)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andDo(document(
                        "ranking/team-member-weekly",
                        pathParameters(
                                parameterWithName("cohort-id").description("기수 ID"),
                                parameterWithName("team-id").description("팀 ID"),
                                parameterWithName("week-start-date").description("주간 시작일")),
                        responseFields(memberHistoricalFields())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("주간 팀 순위 조회")
    void documentsTeamWeeklyRanking() throws Exception {
        // Given: 주간 팀 순위 응답 준비
        LocalDate date = LocalDate.of(2000, 1, 10);
        TeamStudyRankingViewResult view =
                new TeamStudyRankingViewResult(
                        new TeamStudyRankingBoardResult(
                                1L,
                                List.of(
                                        new TeamStudyRankingEntryResult(
                                                1L, TEAM_ID, "첫 팀", 7200L))),
                        new MyTeamStudyRankingResult(Optional.empty()));
        given(
                        teamStudyRankingQueryService.getHistoricalTeamView(
                                USER_ID,
                                COHORT_ID,
                                StudyRankingPeriodSelection.weekly(date),
                                new StudyRankingQuery(null)))
                .willReturn(
                        new HistoricalStudyRankingResult<>(
                                date, Optional.of(date.plusDays(4)), view));

        // When & Then
        mockMvc.perform(get(
                                "/api/v1/cohorts/{cohort-id}/study-rankings/teams/weekly/{week-start-date}",
                                COHORT_ID,
                                date)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andDo(document(
                        "ranking/team-weekly",
                        pathParameters(
                                parameterWithName("cohort-id").description("기수 ID"),
                                parameterWithName("week-start-date").description("주간 시작일")),
                        responseFields(teamHistoricalFields())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("월간 팀 순위 조회")
    void documentsTeamMonthlyRanking() throws Exception {
        // Given: 월간 팀 순위 응답 준비
        YearMonth month = YearMonth.of(2000, 1);
        TeamStudyRankingViewResult view =
                new TeamStudyRankingViewResult(
                        new TeamStudyRankingBoardResult(
                                1L,
                                List.of(
                                        new TeamStudyRankingEntryResult(
                                                1L, TEAM_ID, "첫 팀", 7200L))),
                        new MyTeamStudyRankingResult(Optional.empty()));
        given(
                        teamStudyRankingQueryService.getHistoricalTeamView(
                                USER_ID,
                                COHORT_ID,
                                StudyRankingPeriodSelection.monthly(month),
                                new StudyRankingQuery(null)))
                .willReturn(
                        new HistoricalStudyRankingResult<>(
                                month.atDay(1), Optional.of(month.atEndOfMonth()), view));

        // When & Then
        mockMvc.perform(get(
                                "/api/v1/cohorts/{cohort-id}/study-rankings/teams/monthly/{month}",
                                COHORT_ID,
                                month)
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andDo(document(
                        "ranking/team-monthly",
                        pathParameters(
                                parameterWithName("cohort-id").description("기수 ID"),
                                parameterWithName("month").description("월 (yyyy-MM)")),
                        responseFields(teamHistoricalFields())))
                .andExpect(status().isOk());
    }

    @Nested
    @DisplayName("팀 내부 개인 랭킹")
    class TeamMemberRanking {

        @Test
        @DisplayName("오늘 팀원 필터와 타이머 상태 정상 응답")
        void returnsTodayTeamMemberRanking() throws Exception {
            MemberStudyRankingViewResult view = new MemberStudyRankingViewResult(
                    new StudyRankingBoardResult(
                            1L,
                            List.of(new StudyRankingEntryResult(
                                    1L,
                                    "첫째",
                                    7_200L,
                                    true
                            ))
                    ),
                    new MyStudyRankingResult(1L, Optional.empty())
            );
            given(studyRankingQueryService.getTodayTeamMemberView(
                    USER_ID,
                    COHORT_ID,
                    TEAM_ID,
                    new StudyRankingQuery(2)
            )).willReturn(new TodayStudyRankingResult<>(
                    AGGREGATION_DATE,
                    CALCULATED_AT,
                    view
            ));

            mockMvc.perform(get(
                                    "/api/v1/cohorts/{cohort-id}/teams/{team-id}/study-rankings/today",
                                    COHORT_ID,
                                    TEAM_ID)
                            .queryParam("maxRank", "2")
                            .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                    .andDo(document(
                            "ranking/team-member-today",
                            pathParameters(
                                    parameterWithName("cohort-id").description("조회할 기수 ID"),
                                    parameterWithName("team-id").description("조회할 팀 ID")),
                            queryParameters(parameterWithName("maxRank").optional().description("반환할 최대 순위")),
                            responseFields(memberTodayFields())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.aggregationDate").value("2000-01-13"))
                    .andExpect(jsonPath("$.rankedMemberCount").value(1))
                    .andExpect(jsonPath("$.entries[0].timerRunning").value(true))
                    .andExpect(jsonPath("$.myRanking.ranked").value(false));
        }

        @Test
        @DisplayName("확정 월간 응답에 타이머 상태를 노출하지 않음")
        void returnsHistoricalTeamMemberRankingWithoutTimerState() throws Exception {
            YearMonth month = YearMonth.parse("1999-12");
            MemberStudyRankingViewResult view = new MemberStudyRankingViewResult(
                    new StudyRankingBoardResult(
                            1L,
                            List.of(new StudyRankingEntryResult(1L, "첫째", 7_200L))
                    ),
                    new MyStudyRankingResult(1L, Optional.empty())
            );
            given(studyRankingQueryService.getHistoricalTeamMemberView(
                    USER_ID,
                    COHORT_ID,
                    TEAM_ID,
                    StudyRankingPeriodSelection.monthly(month),
                    new StudyRankingQuery(null)
            )).willReturn(new HistoricalStudyRankingResult<>(
                    month.atDay(1),
                    Optional.of(month.atEndOfMonth()),
                    view
            ));

            mockMvc.perform(get(
                                    "/api/v1/cohorts/{cohort-id}/teams/{team-id}"
                                            + "/study-rankings/monthly/{month}",
                                    COHORT_ID,
                                    TEAM_ID,
                                    month)
                            .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                    .andDo(document(
                            "ranking/team-member-monthly",
                            pathParameters(
                                    parameterWithName("cohort-id").description("조회할 기수 ID"),
                                    parameterWithName("team-id").description("조회할 팀 ID"),
                                    parameterWithName("month")
                                            .description("조회할 월 (yyyy-MM)")),
                            responseFields(memberHistoricalFields())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.startDate").value("1999-12-01"))
                    .andExpect(jsonPath("$.includedThroughDate").value("1999-12-31"))
                    .andExpect(jsonPath("$.entries[0].timerRunning").doesNotExist());
        }
    }

    @Nested
    @DisplayName("팀 간 랭킹")
    class TeamRanking {

        @Test
        @DisplayName("오늘 팀 합산 순위 정상 응답")
        void returnsTodayTeamRanking() throws Exception {
            TeamStudyRankingEntryResult leader = new TeamStudyRankingEntryResult(
                    1L,
                    TEAM_ID,
                    "첫 팀",
                    9_000L
            );
            TeamStudyRankingEntryResult mine = new TeamStudyRankingEntryResult(
                    2L,
                    200L,
                    "내 팀",
                    5_400L
            );
            TeamStudyRankingViewResult view = new TeamStudyRankingViewResult(
                    new TeamStudyRankingBoardResult(2L, List.of(leader)),
                    new MyTeamStudyRankingResult(Optional.of(mine))
            );
            given(teamStudyRankingQueryService.getTodayTeamView(
                    USER_ID,
                    COHORT_ID,
                    new StudyRankingQuery(1)
            )).willReturn(new TodayStudyRankingResult<>(
                    AGGREGATION_DATE,
                    CALCULATED_AT,
                    view
            ));

            mockMvc.perform(get("/api/v1/cohorts/{cohort-id}/study-rankings/teams/today", COHORT_ID)
                            .queryParam("maxRank", "1")
                            .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                    .andDo(document(
                            "ranking/team-today",
                            pathParameters(parameterWithName("cohort-id").description("조회할 기수 ID")),
                            queryParameters(parameterWithName("maxRank").optional().description("반환할 최대 순위")),
                            responseFields(teamTodayFields())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rankedTeamCount").value(2))
                    .andExpect(jsonPath("$.returnedEntryCount").value(1))
                    .andExpect(jsonPath("$.entries[0].teamId").value(TEAM_ID))
                    .andExpect(jsonPath("$.entries[0].studySeconds").value(9_000L))
                    .andExpect(jsonPath("$.entries[0].timerRunning").doesNotExist())
                    .andExpect(jsonPath("$.myTeamRanking.ranking.rank").value(2));
        }

        @Test
        @DisplayName("확정 일간 팀 합계 정상 응답")
        void returnsHistoricalTeamRanking() throws Exception {
            LocalDate date = LocalDate.parse("2000-01-12");
            TeamStudyRankingViewResult view = new TeamStudyRankingViewResult(
                    new TeamStudyRankingBoardResult(
                            1L,
                            List.of(new TeamStudyRankingEntryResult(
                                    1L,
                                    TEAM_ID,
                                    "첫 팀",
                                    7_200L
                            ))
                    ),
                    new MyTeamStudyRankingResult(Optional.empty())
            );
            given(teamStudyRankingQueryService.getHistoricalTeamView(
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
                                    "/api/v1/cohorts/{cohort-id}"
                                            + "/study-rankings/teams/daily/{date}",
                                    COHORT_ID,
                                    date)
                            .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                    .andDo(document(
                            "ranking/team-daily",
                            pathParameters(
                                    parameterWithName("cohort-id").description("조회할 기수 ID"),
                                    parameterWithName("date")
                                            .description("조회할 날짜 (yyyy-MM-dd)")),
                            responseFields(teamHistoricalFields())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.startDate").value("2000-01-12"))
                    .andExpect(jsonPath("$.includedThroughDate").value("2000-01-12"))
                    .andExpect(jsonPath("$.calculatedAt").doesNotExist())
                    .andExpect(jsonPath("$.myTeamRanking.ranked").value(false));
        }

        @Test
        @DisplayName("일간 날짜 형식 오류 요청 거부")
        void rejectsInvalidDailyDateFormat() throws Exception {
            mockMvc.perform(get(
                                    "/api/v1/cohorts/{cohort-id}"
                                            + "/study-rankings/teams/daily/{date}",
                                    COHORT_ID,
                                    "not-a-date")
                            .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(CommonErrorCode.INVALID_REQUEST.code()));

            verifyNoInteractions(teamStudyRankingQueryService);
        }
    }

    private FieldDescriptor[] memberTodayFields() {
        return new FieldDescriptor[] {
            fieldWithPath("aggregationDate").type(JsonFieldType.STRING).description("현재 집계 기준일"),
            fieldWithPath("calculatedAt")
                    .type(JsonFieldType.STRING)
                    .description("응답 계산 시각 (ISO-8601 UTC)"),
            fieldWithPath("rankedMemberCount").type(JsonFieldType.NUMBER).description("전체 순위 수"),
            fieldWithPath("returnedEntryCount").type(JsonFieldType.NUMBER).description("반환 행 수"),
            fieldWithPath("entries").type(JsonFieldType.ARRAY).description("수강생 순위"),
            fieldWithPath("entries[].rank").type(JsonFieldType.NUMBER).description("순위"),
            fieldWithPath("entries[].displayName").type(JsonFieldType.STRING).description("표시 이름"),
            fieldWithPath("entries[].studySeconds")
                    .type(JsonFieldType.NUMBER)
                    .description("공부 시간 (초)"),
            fieldWithPath("entries[].timerRunning")
                    .type(JsonFieldType.BOOLEAN)
                    .description("타이머 실행 여부"),
            fieldWithPath("entries[].characterType")
                    .type(JsonFieldType.STRING)
                    .optional()
                    .description("캐릭터 유형"),
            fieldWithPath("entries[].colorId")
                    .type(JsonFieldType.STRING)
                    .optional()
                    .description("캐릭터 색상"),
            fieldWithPath("entries[].attendanceStreakDays")
                    .type(JsonFieldType.NUMBER)
                    .description("연속 출석일"),
            fieldWithPath("myRanking.ranked").type(JsonFieldType.BOOLEAN).description("내 순위 존재 여부"),
            fieldWithPath("myRanking.ranking")
                    .type(JsonFieldType.OBJECT)
                    .optional()
                    .description("내 순위")
        };
    }

    private FieldDescriptor[] memberHistoricalFields() {
        return new FieldDescriptor[] {
            fieldWithPath("startDate").type(JsonFieldType.STRING).description("기간 시작일"),
            fieldWithPath("includedThroughDate")
                    .type(JsonFieldType.STRING)
                    .optional()
                    .description("포함된 마지막 확정일"),
            fieldWithPath("rankedMemberCount").type(JsonFieldType.NUMBER).description("전체 순위 수"),
            fieldWithPath("returnedEntryCount").type(JsonFieldType.NUMBER).description("반환 행 수"),
            fieldWithPath("entries").type(JsonFieldType.ARRAY).description("확정 수강생 순위"),
            fieldWithPath("entries[].rank").type(JsonFieldType.NUMBER).description("순위"),
            fieldWithPath("entries[].displayName").type(JsonFieldType.STRING).description("표시 이름"),
            fieldWithPath("entries[].studySeconds")
                    .type(JsonFieldType.NUMBER)
                    .description("공부 시간 (초)"),
            fieldWithPath("entries[].characterType")
                    .type(JsonFieldType.STRING)
                    .optional()
                    .description("캐릭터 유형"),
            fieldWithPath("entries[].colorId")
                    .type(JsonFieldType.STRING)
                    .optional()
                    .description("캐릭터 색상"),
            fieldWithPath("entries[].attendanceStreakDays")
                    .type(JsonFieldType.NUMBER)
                    .description("연속 출석일"),
            fieldWithPath("myRanking.ranked").type(JsonFieldType.BOOLEAN).description("내 순위 존재 여부"),
            fieldWithPath("myRanking.ranking")
                    .type(JsonFieldType.OBJECT)
                    .optional()
                    .description("내 순위")
        };
    }

    private FieldDescriptor[] teamTodayFields() {
        return new FieldDescriptor[] {
            fieldWithPath("aggregationDate").type(JsonFieldType.STRING).description("현재 집계 기준일"),
            fieldWithPath("calculatedAt").type(JsonFieldType.STRING).description("응답 계산 시각"),
            fieldWithPath("rankedTeamCount").type(JsonFieldType.NUMBER).description("전체 팀 순위 수"),
            fieldWithPath("returnedEntryCount").type(JsonFieldType.NUMBER).description("반환 행 수"),
            fieldWithPath("entries").type(JsonFieldType.ARRAY).description("팀 순위"),
            fieldWithPath("entries[].rank").type(JsonFieldType.NUMBER).description("순위"),
            fieldWithPath("entries[].teamId").type(JsonFieldType.NUMBER).description("팀 ID"),
            fieldWithPath("entries[].teamName").type(JsonFieldType.STRING).description("팀 이름"),
            fieldWithPath("entries[].studySeconds")
                    .type(JsonFieldType.NUMBER)
                    .description("공부 시간 (초)"),
            fieldWithPath("myTeamRanking.ranked")
                    .type(JsonFieldType.BOOLEAN)
                    .description("내 팀 순위 존재 여부"),
            fieldWithPath("myTeamRanking.ranking")
                    .type(JsonFieldType.OBJECT)
                    .optional()
                    .description("내 팀 순위"),
            fieldWithPath("myTeamRanking.ranking.rank")
                    .type(JsonFieldType.NUMBER)
                    .description("내 팀 순위"),
            fieldWithPath("myTeamRanking.ranking.teamId")
                    .type(JsonFieldType.NUMBER)
                    .description("내 팀 ID"),
            fieldWithPath("myTeamRanking.ranking.teamName")
                    .type(JsonFieldType.STRING)
                    .description("내 팀 이름"),
            fieldWithPath("myTeamRanking.ranking.studySeconds")
                    .type(JsonFieldType.NUMBER)
                    .description("내 팀 공부 시간 (초)")
        };
    }

    private FieldDescriptor[] teamHistoricalFields() {
        return new FieldDescriptor[] {
            fieldWithPath("startDate").type(JsonFieldType.STRING).description("기간 시작일"),
            fieldWithPath("includedThroughDate")
                    .type(JsonFieldType.STRING)
                    .optional()
                    .description("포함된 마지막 확정일"),
            fieldWithPath("rankedTeamCount").type(JsonFieldType.NUMBER).description("전체 팀 순위 수"),
            fieldWithPath("returnedEntryCount").type(JsonFieldType.NUMBER).description("반환 행 수"),
            fieldWithPath("entries").type(JsonFieldType.ARRAY).description("확정 팀 순위"),
            fieldWithPath("entries[].rank").type(JsonFieldType.NUMBER).description("순위"),
            fieldWithPath("entries[].teamId").type(JsonFieldType.NUMBER).description("팀 ID"),
            fieldWithPath("entries[].teamName").type(JsonFieldType.STRING).description("팀 이름"),
            fieldWithPath("entries[].studySeconds")
                    .type(JsonFieldType.NUMBER)
                    .description("공부 시간 (초)"),
            fieldWithPath("myTeamRanking.ranked")
                    .type(JsonFieldType.BOOLEAN)
                    .description("내 팀 순위 존재 여부"),
            fieldWithPath("myTeamRanking.ranking")
                    .type(JsonFieldType.OBJECT)
                    .optional()
                    .description("내 팀 순위")
        };
    }
}
