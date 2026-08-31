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
import site.omagotchi.learningservice.ranking.application.query.StudyRankingPeriodSelection;
import site.omagotchi.learningservice.ranking.application.query.StudyRankingQuery;
import site.omagotchi.learningservice.ranking.application.result.HistoricalStudyRankingResult;
import site.omagotchi.learningservice.ranking.application.result.MemberStudyRankingViewResult;
import site.omagotchi.learningservice.ranking.application.result.MyStudyRankingResult;
import site.omagotchi.learningservice.ranking.application.result.StudyRankingBoardResult;
import site.omagotchi.learningservice.ranking.application.result.StudyRankingEntryResult;
import site.omagotchi.learningservice.ranking.application.result.TodayStudyRankingResult;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

@DisplayName("학습 랭킹 API")
@ExtendWith(MockitoExtension.class)
class StudyRankingControllerTest {

    private static final UUID USER_ID = new UUID(0L, 1L);
    private static final Long COHORT_ID = 10L;
    private static final LocalDate AGGREGATION_DATE = LocalDate.parse("2000-01-13");
    private static final Instant CALCULATED_AT = Instant.parse("2000-01-12T20:00:00Z");
    private static final Instant TOKEN_ISSUED_AT = Instant.parse("2000-01-01T00:00:00Z");
    private static final Instant TOKEN_EXPIRES_AT = Instant.parse("2000-01-01T00:05:00Z");

    @Mock
    private StudyRankingQueryService studyRankingQueryService;

    @InjectMocks
    private MemberStudyRankingController memberStudyRankingController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = standaloneSetup(memberStudyRankingController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
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

            mockMvc.perform(get(
                            "/api/v1/cohorts/{cohort-id}/study-rankings/today",
                            COHORT_ID
                    ).queryParam("maxRank", "2")
                    .principal(authentication()))
                    .andExpect(status().isOk())
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
                            date
                    ).principal(authentication()))
                    .andExpect(status().isOk())
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
                            monday
                    ).principal(authentication()))
                    .andExpect(status().isOk())
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
                            "not-a-date"
                    ).principal(authentication()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code")
                            .value(CommonErrorCode.INVALID_REQUEST.code()));

            verifyNoInteractions(studyRankingQueryService);
        }
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
