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
import site.omagotchi.learningservice.ranking.application.query.StudyRankingPeriod;
import site.omagotchi.learningservice.ranking.application.query.StudyRankingQuery;
import site.omagotchi.learningservice.ranking.application.result.MemberStudyRankingViewResult;
import site.omagotchi.learningservice.ranking.application.result.MyStudyRankingResult;
import site.omagotchi.learningservice.ranking.application.result.StudyRankingBoardResult;
import site.omagotchi.learningservice.ranking.application.result.StudyRankingEntryResult;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    private static final Instant TOKEN_ISSUED_AT = Instant.parse("2000-01-01T00:00:00Z");
    private static final Instant TOKEN_EXPIRES_AT = Instant.parse("2000-01-01T00:05:00Z");

    @Mock
    private StudyRankingQueryService studyRankingQueryService;

    @InjectMocks
    private MemberStudyRankingController memberStudyRankingController;

    @InjectMocks
    private ManagerStudyRankingController managerStudyRankingController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = standaloneSetup(
                memberStudyRankingController,
                managerStudyRankingController
        ).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Nested
    @DisplayName("회원 랭킹 보드 조회")
    class GetMemberRanking {

        @Test
        @DisplayName("최소 메타데이터와 내 순위 정상 응답")
        void returnsCompactBoardAndMyRanking() throws Exception {
            StudyRankingBoardResult board = new StudyRankingBoardResult(
                    3L,
                    List.of(
                            new StudyRankingEntryResult(1L, "첫째", 7_200L),
                            new StudyRankingEntryResult(2L, "둘째", 3_600L)
                    )
            );
            MyStudyRankingResult mine = new MyStudyRankingResult(
                    3L,
                    Optional.of(new StudyRankingEntryResult(3L, "나", 1_800L))
            );
            given(studyRankingQueryService.getMemberView(
                    USER_ID,
                    COHORT_ID,
                    new StudyRankingQuery(StudyRankingPeriod.DAILY, 2)
            )).willReturn(new MemberStudyRankingViewResult(board, mine));

            mockMvc.perform(get(
                            "/api/v1/cohorts/{cohortId}/study-rankings",
                            COHORT_ID
                    ).queryParam("period", "DAILY")
                    .queryParam("maxRank", "2")
                    .principal(authentication()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rankedMemberCount").value(3))
                    .andExpect(jsonPath("$.returnedEntryCount").value(2))
                    .andExpect(jsonPath("$.entries[0].rank").value(1))
                    .andExpect(jsonPath("$.entries[0].displayName").value("첫째"))
                    .andExpect(jsonPath("$.entries[0].studySeconds").value(7_200L))
                    .andExpect(jsonPath("$.myRanking.ranked").value(true))
                    .andExpect(jsonPath("$.myRanking.ranking.rank").value(3))
                    .andExpect(jsonPath("$.period").doesNotExist())
                    .andExpect(jsonPath("$.baseDate").doesNotExist())
                    .andExpect(jsonPath("$.includedThroughDate").doesNotExist())
                    .andExpect(jsonPath("$.periodStatus").doesNotExist())
                    .andExpect(jsonPath("$.calculatedAt").doesNotExist())
                    .andExpect(jsonPath("$.requestedMaxRank").doesNotExist());
        }

        @Test
        @DisplayName("오늘 기간 값 제거 확인")
        void rejectsRemovedTodayPeriod() throws Exception {
            mockMvc.perform(get(
                            "/api/v1/cohorts/{cohortId}/study-rankings",
                            COHORT_ID
                    ).queryParam("period", "TODAY")
                    .principal(authentication()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code")
                            .value(CommonErrorCode.INVALID_REQUEST.code()));

            verifyNoInteractions(studyRankingQueryService);
        }
    }

    @Nested
    @DisplayName("내 랭킹 조회")
    class GetMyRanking {

        @Test
        @DisplayName("미랭크 최소 응답")
        void returnsCompactUnrankedResponse() throws Exception {
            given(studyRankingQueryService.getMine(
                    USER_ID,
                    COHORT_ID,
                    StudyRankingPeriod.DAILY
            )).willReturn(new MyStudyRankingResult(
                    2L,
                    Optional.empty()
            ));

            mockMvc.perform(get(
                            "/api/v1/cohorts/{cohortId}/study-rankings/me",
                            COHORT_ID
                    ).queryParam("period", "DAILY")
                    .principal(authentication()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rankedMemberCount").value(2))
                    .andExpect(jsonPath("$.ranked").value(false))
                    .andExpect(jsonPath("$.ranking").doesNotExist())
                    .andExpect(jsonPath("$.entries").doesNotExist())
                    .andExpect(jsonPath("$.returnedEntryCount").doesNotExist());
        }
    }

    @Nested
    @DisplayName("관리자 랭킹 보드 조회")
    class GetManagerRanking {

        @Test
        @DisplayName("내 순위 없는 최소 응답")
        void returnsCompactBoardWithoutMyRanking() throws Exception {
            given(studyRankingQueryService.getManagerBoard(
                    USER_ID,
                    COHORT_ID,
                    new StudyRankingQuery(StudyRankingPeriod.WEEKLY, null)
            )).willReturn(new StudyRankingBoardResult(
                    1L,
                    List.of(new StudyRankingEntryResult(1L, "첫째", 3_600L))
            ));

            mockMvc.perform(get(
                            "/api/v1/cohorts/{cohortId}/study-rankings/management",
                            COHORT_ID
                    ).queryParam("period", "WEEKLY")
                    .principal(authentication()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rankedMemberCount").value(1))
                    .andExpect(jsonPath("$.returnedEntryCount").value(1))
                    .andExpect(jsonPath("$.entries[0].rank").value(1))
                    .andExpect(jsonPath("$.myRanking").doesNotExist())
                    .andExpect(jsonPath("$.requestedMaxRank").doesNotExist());
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
