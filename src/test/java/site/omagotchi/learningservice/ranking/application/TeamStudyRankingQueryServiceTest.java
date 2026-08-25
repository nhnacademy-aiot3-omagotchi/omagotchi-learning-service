package site.omagotchi.learningservice.ranking.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.cohort.application.result.CohortMembershipView;
import site.omagotchi.learningservice.cohort.application.CohortErrorCode;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.ranking.application.query.StudyRankingPeriodSelection;
import site.omagotchi.learningservice.ranking.application.query.StudyRankingQuery;
import site.omagotchi.learningservice.ranking.application.result.HistoricalStudyRankingResult;
import site.omagotchi.learningservice.ranking.application.result.TeamStudyRankingEntryResult;
import site.omagotchi.learningservice.ranking.application.result.TeamStudyRankingViewResult;
import site.omagotchi.learningservice.ranking.application.result.TodayStudyRankingResult;
import site.omagotchi.learningservice.study.application.StudyRecordAggregationQueryService;
import site.omagotchi.learningservice.study.application.result.MemberCurrentStudyDurationResult;
import site.omagotchi.learningservice.study.application.result.MemberStudyDurationResult;
import site.omagotchi.learningservice.team.application.CurrentTeamMembershipQueryService;
import site.omagotchi.learningservice.team.application.result.CurrentTeamMembershipView;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("팀 간 학습 랭킹 조회")
@ExtendWith(MockitoExtension.class)
class TeamStudyRankingQueryServiceTest {

    private static final UUID USER_ID = new UUID(0L, 1L);
    private static final Long COHORT_ID = 10L;
    private static final Long MEMBERSHIP_ID = 11L;
    private static final Instant CALCULATED_AT = Instant.parse("2000-01-12T20:00:00Z");

    @Mock
    private CohortAccessService cohortAccessService;

    @Mock
    private CohortMembershipQueryService cohortMembershipQueryService;

    @Mock
    private CurrentTeamMembershipQueryService currentTeamMembershipQueryService;

    @Mock
    private StudyRecordAggregationQueryService studyRecordAggregationQueryService;

    @Mock
    private Clock clock;

    @InjectMocks
    private TeamStudyRankingQueryService queryService;

    @Nested
    @DisplayName("오늘 팀 순위")
    class Today {

        @Test
        @DisplayName("현재 팀원 학습시간 합계와 동점 경계 정상 처리")
        void ranksCurrentTeamsByMemberDurationSum() {
            givenStudentMembership();
            given(clock.instant()).willReturn(CALCULATED_AT);
            givenCurrentTeamInput();
            given(studyRecordAggregationQueryService.getCurrentDurations(
                    List.of(MEMBERSHIP_ID, 20L, 21L, 22L, 23L),
                    CALCULATED_AT
            )).willReturn(List.of(
                    new MemberCurrentStudyDurationResult(MEMBERSHIP_ID, 1_800L, false),
                    new MemberCurrentStudyDurationResult(20L, 7_200L, true),
                    new MemberCurrentStudyDurationResult(21L, 1_800L, false),
                    new MemberCurrentStudyDurationResult(22L, 3_600L, false),
                    new MemberCurrentStudyDurationResult(23L, 5_400L, true)
            ));

            TodayStudyRankingResult<TeamStudyRankingViewResult> result =
                    queryService.getTodayTeamView(
                            USER_ID,
                            COHORT_ID,
                            new StudyRankingQuery(2)
                    );

            assertAll(
                    () -> assertEquals(
                            LocalDate.parse("2000-01-13"),
                            result.aggregationDate()
                    ),
                    () -> assertEquals(3L, result.ranking().board().rankedTeamCount()),
                    () -> assertEquals(
                            List.of(1L, 2L, 2L),
                            result.ranking().board().entries().stream()
                                    .map(TeamStudyRankingEntryResult::rank)
                                    .toList()
                    ),
                    () -> assertTrue(result.ranking().mine().ranked()),
                    () -> assertEquals(
                            2L,
                            result.ranking().mine().ranking().orElseThrow().rank()
                    )
            );
            verify(cohortMembershipQueryService).findActiveStudentMemberships(COHORT_ID);
            verify(currentTeamMembershipQueryService).findCurrentMemberships(
                    COHORT_ID,
                    List.of(MEMBERSHIP_ID, 20L, 21L, 22L, 23L)
            );
            verify(studyRecordAggregationQueryService).getCurrentDurations(
                    List.of(MEMBERSHIP_ID, 20L, 21L, 22L, 23L),
                    CALCULATED_AT
            );
        }

        @Test
        @DisplayName("합계가 0인 팀은 순위 대상 제외")
        void excludesTeamWithZeroDuration() {
            givenStudentMembership();
            given(clock.instant()).willReturn(CALCULATED_AT);
            given(cohortMembershipQueryService.findActiveStudentMemberships(COHORT_ID))
                    .willReturn(List.of(membership(MEMBERSHIP_ID), membership(20L)));
            given(currentTeamMembershipQueryService.findCurrentMemberships(
                    COHORT_ID,
                    List.of(MEMBERSHIP_ID, 20L)
            )).willReturn(List.of(
                    teamMembership(200L, "0초 팀", MEMBERSHIP_ID),
                    teamMembership(100L, "공부한 팀", 20L)
            ));
            given(studyRecordAggregationQueryService.getCurrentDurations(
                    List.of(MEMBERSHIP_ID, 20L),
                    CALCULATED_AT
            )).willReturn(List.of(
                    new MemberCurrentStudyDurationResult(20L, 3_600L, false)
            ));

            TodayStudyRankingResult<TeamStudyRankingViewResult> result =
                    queryService.getTodayTeamView(
                            USER_ID,
                            COHORT_ID,
                            new StudyRankingQuery(null)
                    );

            assertAll(
                    () -> assertEquals(1L, result.ranking().board().rankedTeamCount()),
                    () -> assertEquals(
                            List.of(100L),
                            result.ranking().board().entries().stream()
                                    .map(TeamStudyRankingEntryResult::teamId)
                                    .toList()
                    ),
                    () -> assertFalse(result.ranking().mine().ranked())
            );
        }

        @Test
        @DisplayName("요청자 팀이 maxRank 밖이어도 내 팀 순위 반환")
        void returnsMyTeamOutsideMaxRank() {
            givenStudentMembership();
            given(clock.instant()).willReturn(CALCULATED_AT);
            given(cohortMembershipQueryService.findActiveStudentMemberships(COHORT_ID))
                    .willReturn(List.of(membership(MEMBERSHIP_ID), membership(20L)));
            given(currentTeamMembershipQueryService.findCurrentMemberships(
                    COHORT_ID,
                    List.of(MEMBERSHIP_ID, 20L)
            )).willReturn(List.of(
                    teamMembership(200L, "내 팀", MEMBERSHIP_ID),
                    teamMembership(100L, "상위 팀", 20L)
            ));
            given(studyRecordAggregationQueryService.getCurrentDurations(
                    List.of(MEMBERSHIP_ID, 20L),
                    CALCULATED_AT
            )).willReturn(List.of(
                    new MemberCurrentStudyDurationResult(MEMBERSHIP_ID, 1_800L, false),
                    new MemberCurrentStudyDurationResult(20L, 3_600L, false)
            ));

            TodayStudyRankingResult<TeamStudyRankingViewResult> result =
                    queryService.getTodayTeamView(
                            USER_ID,
                            COHORT_ID,
                            new StudyRankingQuery(1)
                    );

            assertAll(
                    () -> assertEquals(2L, result.ranking().board().rankedTeamCount()),
                    () -> assertEquals(1, result.ranking().board().entries().size()),
                    () -> assertEquals(
                            2L,
                            result.ranking().mine().ranking().orElseThrow().rank()
                    ),
                    () -> assertEquals(
                            200L,
                            result.ranking().mine().ranking().orElseThrow().teamId()
                    )
            );
        }

        @Test
        @DisplayName("활성 학생 권한이 없으면 팀 조회 전 예외")
        void rejectsNonStudentBeforeReadingTeams() {
            willThrow(new BusinessException(CohortErrorCode.COHORT_ACCESS_DENIED))
                    .given(cohortAccessService)
                    .requireActiveStudentMembershipId(COHORT_ID, USER_ID);

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> queryService.getTodayTeamView(
                            USER_ID,
                            COHORT_ID,
                            new StudyRankingQuery(null)
                    )
            );

            assertEquals(CohortErrorCode.COHORT_ACCESS_DENIED, exception.getErrorCode());
            verifyNoInteractions(
                    clock,
                    cohortMembershipQueryService,
                    currentTeamMembershipQueryService,
                    studyRecordAggregationQueryService
            );
        }
    }

    @Nested
    @DisplayName("확정 기간 팀 순위")
    class Historical {

        @Test
        @DisplayName("과거 학습시간을 조회 시점의 현재 팀으로 합산")
        void groupsHistoricalDurationByCurrentTeam() {
            LocalDate date = LocalDate.parse("2000-01-12");
            givenStudentMembership();
            given(clock.instant()).willReturn(CALCULATED_AT);
            List<CohortMembershipView> memberships = List.of(
                    membership(MEMBERSHIP_ID),
                    membership(20L)
            );
            given(cohortMembershipQueryService.findActiveStudentMemberships(COHORT_ID))
                    .willReturn(memberships);
            given(currentTeamMembershipQueryService.findCurrentMemberships(
                    COHORT_ID,
                    List.of(MEMBERSHIP_ID, 20L)
            )).willReturn(List.of(
                    teamMembership(200L, "현재 팀", MEMBERSHIP_ID),
                    teamMembership(200L, "현재 팀", 20L)
            ));
            given(studyRecordAggregationQueryService.getConfirmedDurations(
                    List.of(MEMBERSHIP_ID, 20L),
                    date,
                    date
            )).willReturn(List.of(
                    new MemberStudyDurationResult(MEMBERSHIP_ID, 3_600L),
                    new MemberStudyDurationResult(20L, 1_800L)
            ));

            HistoricalStudyRankingResult<TeamStudyRankingViewResult> result =
                    queryService.getHistoricalTeamView(
                            USER_ID,
                            COHORT_ID,
                            StudyRankingPeriodSelection.daily(date),
                            new StudyRankingQuery(null)
                    );

            TeamStudyRankingEntryResult entry = result.ranking()
                    .board()
                    .entries()
                    .getFirst();
            assertAll(
                    () -> assertEquals(200L, entry.teamId()),
                    () -> assertEquals(5_400L, entry.studySeconds()),
                    () -> assertTrue(result.ranking().mine().ranked())
            );
        }

        @Test
        @DisplayName("팀이 없는 요청자는 내 팀 순위 미참여 처리")
        void returnsUnrankedMineWhenRequesterHasNoTeam() {
            LocalDate date = LocalDate.parse("2000-01-12");
            givenStudentMembership();
            given(clock.instant()).willReturn(CALCULATED_AT);
            given(cohortMembershipQueryService.findActiveStudentMemberships(COHORT_ID))
                    .willReturn(List.of(membership(MEMBERSHIP_ID), membership(20L)));
            given(currentTeamMembershipQueryService.findCurrentMemberships(
                    COHORT_ID,
                    List.of(MEMBERSHIP_ID, 20L)
            )).willReturn(List.of(teamMembership(100L, "다른 팀", 20L)));
            given(studyRecordAggregationQueryService.getConfirmedDurations(
                    List.of(20L),
                    date,
                    date
            )).willReturn(List.of(new MemberStudyDurationResult(20L, 1_800L)));

            HistoricalStudyRankingResult<TeamStudyRankingViewResult> result =
                    queryService.getHistoricalTeamView(
                            USER_ID,
                            COHORT_ID,
                            StudyRankingPeriodSelection.daily(date),
                            new StudyRankingQuery(null)
                    );

            assertFalse(result.ranking().mine().ranked());
        }
    }

    private void givenStudentMembership() {
        given(cohortAccessService.requireActiveStudentMembershipId(COHORT_ID, USER_ID))
                .willReturn(MEMBERSHIP_ID);
    }

    private void givenCurrentTeamInput() {
        List<CohortMembershipView> memberships = List.of(
                membership(MEMBERSHIP_ID),
                membership(20L),
                membership(21L),
                membership(22L),
                membership(23L)
        );
        given(cohortMembershipQueryService.findActiveStudentMemberships(COHORT_ID))
                .willReturn(memberships);
        given(currentTeamMembershipQueryService.findCurrentMemberships(
                COHORT_ID,
                List.of(MEMBERSHIP_ID, 20L, 21L, 22L, 23L)
        )).willReturn(List.of(
                teamMembership(200L, "둘째 팀", MEMBERSHIP_ID),
                teamMembership(100L, "첫째 팀", 20L),
                teamMembership(100L, "첫째 팀", 21L),
                teamMembership(200L, "둘째 팀", 22L),
                teamMembership(300L, "셋째 팀", 23L)
        ));
    }

    private CohortMembershipView membership(Long membershipId) {
        return new CohortMembershipView(
                membershipId,
                COHORT_ID,
                new UUID(0L, membershipId)
        );
    }

    private CurrentTeamMembershipView teamMembership(
            Long teamId,
            String teamName,
            Long membershipId
    ) {
        return new CurrentTeamMembershipView(teamId, teamName, membershipId);
    }
}
