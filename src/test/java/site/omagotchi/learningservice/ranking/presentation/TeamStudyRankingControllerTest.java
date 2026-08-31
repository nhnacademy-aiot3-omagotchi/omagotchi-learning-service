package site.omagotchi.learningservice.ranking.presentation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;
import site.omagotchi.learningservice.global.exception.GlobalExceptionHandler;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@DisplayName("팀 학습 랭킹 API")
@ExtendWith(MockitoExtension.class)
class TeamStudyRankingControllerTest {

    private static final UUID USER_ID = new UUID(0L, 1L);
    private static final Long COHORT_ID = 10L;
    private static final Long TEAM_ID = 100L;
    private static final LocalDate AGGREGATION_DATE = LocalDate.parse("2000-01-13");
    private static final Instant CALCULATED_AT = Instant.parse("2000-01-12T20:00:00Z");
    private static final Instant TOKEN_ISSUED_AT = Instant.parse("2000-01-01T00:00:00Z");
    private static final Instant TOKEN_EXPIRES_AT = Instant.parse("2000-01-01T00:05:00Z");

    @Mock
    private StudyRankingQueryService studyRankingQueryService;

    @Mock
    private TeamStudyRankingQueryService teamStudyRankingQueryService;

    @InjectMocks
    private TeamMemberStudyRankingController teamMemberStudyRankingController;

    @InjectMocks
    private TeamStudyRankingController teamStudyRankingController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = standaloneSetup(
                teamMemberStudyRankingController,
                teamStudyRankingController
        )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
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
                            TEAM_ID
                    ).queryParam("maxRank", "2")
                    .principal(authentication()))
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
                            month
                    ).principal(authentication()))
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

            mockMvc.perform(get(
                            "/api/v1/cohorts/{cohort-id}/study-rankings/teams/today",
                            COHORT_ID
                    ).queryParam("maxRank", "1")
                    .principal(authentication()))
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
                            date
                    ).principal(authentication()))
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
                            "not-a-date"
                    ).principal(authentication()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code")
                            .value(CommonErrorCode.INVALID_REQUEST.code()));

            verifyNoInteractions(teamStudyRankingQueryService);
        }
    }

    private JwtAuthenticationToken authentication() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .subject(USER_ID.toString())
                .claim("role", "USER")
                .issuedAt(TOKEN_ISSUED_AT)
                .expiresAt(TOKEN_EXPIRES_AT)
                .build();
        return new JwtAuthenticationToken(jwt);
    }
}
