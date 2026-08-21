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
import site.omagotchi.learningservice.cohort.domain.CohortErrorCode;
import site.omagotchi.learningservice.gamification.application.CharacterGrowthService;
import site.omagotchi.learningservice.gamification.application.result.RepresentativeCharacterResult;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.ranking.application.query.StudyRankingPeriodSelection;
import site.omagotchi.learningservice.ranking.application.query.StudyRankingQuery;
import site.omagotchi.learningservice.ranking.application.result.HistoricalStudyRankingResult;
import site.omagotchi.learningservice.ranking.application.result.MemberStudyRankingViewResult;
import site.omagotchi.learningservice.ranking.application.result.StudyRankingEntryResult;
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
import java.util.Optional;
import java.util.Set;
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

@DisplayName("학습 랭킹 조회")
@ExtendWith(MockitoExtension.class)
class StudyRankingQueryServiceTest {

    private static final UUID USER_ID = new UUID(0L, 1L);
    private static final UUID LEADER_USER_ID = new UUID(0L, 2L);
    private static final UUID FIRST_TIE_USER_ID = new UUID(0L, 3L);
    private static final UUID SECOND_TIE_USER_ID = new UUID(0L, 4L);
    private static final Long COHORT_ID = 10L;
    private static final Long MEMBERSHIP_ID = 11L;
    private static final Instant CALCULATED_AT = Instant.parse("2000-01-12T20:00:00Z");
    private static final LocalDate CURRENT_AGGREGATION_DATE = LocalDate.parse("2000-01-13");

    @Mock
    private CohortAccessService cohortAccessService;

    @Mock
    private CohortMembershipQueryService cohortMembershipQueryService;

    @Mock
    private StudyRecordAggregationQueryService studyRecordAggregationQueryService;

    @Mock
    private CharacterGrowthService characterGrowthService;

    @Mock
    private CurrentTeamMembershipQueryService currentTeamMembershipQueryService;

    @Mock
    private Clock clock;

    @InjectMocks
    private StudyRankingQueryService studyRankingQueryService;

    @Nested
    @DisplayName("오늘 회원 보드와 내 순위 조회")
    class GetTodayMemberView {

        @Test
        @DisplayName("실행 중 시간과 동점 경계를 같은 기준 시각으로 반환")
        void returnsLiveBoardAndMineFromSameCalculation() {
            List<CohortMembershipView> memberships = memberships();
            givenStudentMembership();
            given(clock.instant()).willReturn(CALCULATED_AT);
            given(cohortMembershipQueryService.findActiveStudentMemberships(COHORT_ID))
                    .willReturn(memberships);
            given(studyRecordAggregationQueryService.getCurrentDurations(
                    membershipIds(memberships),
                    CALCULATED_AT
            )).willReturn(currentDurations());
            givenDisplayNames(Set.of(
                    LEADER_USER_ID,
                    FIRST_TIE_USER_ID,
                    SECOND_TIE_USER_ID,
                    USER_ID
            ));

            TodayStudyRankingResult<MemberStudyRankingViewResult> result =
                    studyRankingQueryService.getTodayMemberView(
                            USER_ID,
                            COHORT_ID,
                            new StudyRankingQuery(2)
                    );

            assertAll(
                    () -> assertEquals(CURRENT_AGGREGATION_DATE, result.aggregationDate()),
                    () -> assertEquals(CALCULATED_AT, result.calculatedAt()),
                    () -> assertEquals(4L, result.ranking().board().rankedMemberCount()),
                    () -> assertEquals(3, result.ranking().board().entries().size()),
                    () -> assertEquals(
                            List.of(1L, 2L, 2L),
                            result.ranking().board().entries().stream()
                                    .map(StudyRankingEntryResult::rank)
                                    .toList()
                    ),
                    () -> assertTrue(result.ranking().board().entries().getFirst().timerRunning()),
                    () -> assertFalse(result.ranking().board().entries().get(1).timerRunning()),
                    () -> assertTrue(result.ranking().mine().ranked()),
                    () -> assertEquals(
                            4L,
                            result.ranking().mine().ranking().orElseThrow().rank()
                    )
            );
            verify(clock).instant();
        }

        @Test
        @DisplayName("활성 학생 권한이 없으면 집계 전 예외")
        void rejectsNonStudentBeforeReadingRanking() {
            willThrow(new BusinessException(CohortErrorCode.COHORT_ACCESS_DENIED))
                    .given(cohortAccessService)
                    .requireActiveStudentMembershipId(COHORT_ID, USER_ID);


            BusinessException exception =
                    assertThrows(
                    BusinessException.class,
                    () -> studyRankingQueryService.getTodayMemberView(
                            USER_ID,
                            COHORT_ID,
                            new StudyRankingQuery(null)
                    )
            );

            assertEquals(CohortErrorCode.COHORT_ACCESS_DENIED, exception.getErrorCode());
            verifyNoInteractions(
                    clock,
                    cohortMembershipQueryService,
                    studyRecordAggregationQueryService,
                    characterGrowthService
            );
        }
    }

    @Nested
    @DisplayName("확정 기간 회원 보드와 내 순위 조회")
    class GetHistoricalMemberView {

        @Test
        @DisplayName("현재 주는 전날까지 확정 기록만 반환")
        void returnsCurrentWeekThroughYesterday() {
            List<CohortMembershipView> memberships = memberships();
            LocalDate weekStartDate = LocalDate.parse("2000-01-10");
            givenStudentMembership();
            given(clock.instant()).willReturn(CALCULATED_AT);
            given(cohortMembershipQueryService.findActiveStudentMemberships(COHORT_ID))
                    .willReturn(memberships);
            given(studyRecordAggregationQueryService.getConfirmedDurations(
                    membershipIds(memberships),
                    weekStartDate,
                    LocalDate.parse("2000-01-12")
            )).willReturn(confirmedDurations());
            givenDisplayNames(Set.of(
                    LEADER_USER_ID,
                    FIRST_TIE_USER_ID,
                    SECOND_TIE_USER_ID,
                    USER_ID
            ));

            HistoricalStudyRankingResult<MemberStudyRankingViewResult> result =
                    studyRankingQueryService.getHistoricalMemberView(
                            USER_ID,
                            COHORT_ID,
                            StudyRankingPeriodSelection.weekly(weekStartDate),
                            new StudyRankingQuery(2)
                    );

            assertAll(
                    () -> assertEquals(weekStartDate, result.startDate()),
                    () -> assertEquals(
                            Optional.of(LocalDate.parse("2000-01-12")),
                            result.includedThroughDate()
                    ),
                    () -> assertEquals(3, result.ranking().board().entries().size()),
                    () -> assertTrue(result.ranking().board().entries().stream()
                            .noneMatch(StudyRankingEntryResult::timerRunning))
            );
        }

        @Test
        @DisplayName("확정 집계일이 없으면 기록 합계 조회 없이 빈 랭킹 반환")
        void returnsEmptyRankingWithoutClosedDate() {
            LocalDate monday = LocalDate.parse("2000-01-10");
            Instant mondayCalculatedAt = Instant.parse("2000-01-09T20:00:00Z");
            List<CohortMembershipView> memberships = memberships();
            givenStudentMembership();
            given(clock.instant()).willReturn(mondayCalculatedAt);
            given(cohortMembershipQueryService.findActiveStudentMemberships(COHORT_ID))
                    .willReturn(memberships);

            HistoricalStudyRankingResult<MemberStudyRankingViewResult> result =
                    studyRankingQueryService.getHistoricalMemberView(
                            USER_ID,
                            COHORT_ID,
                            StudyRankingPeriodSelection.weekly(monday),
                            new StudyRankingQuery(null)
                    );

            assertAll(
                    () -> assertEquals(Optional.empty(), result.includedThroughDate()),
                    () -> assertEquals(0L, result.ranking().board().rankedMemberCount()),
                    () -> assertEquals(List.of(), result.ranking().board().entries()),
                    () -> assertFalse(result.ranking().mine().ranked())
            );
            verifyNoInteractions(studyRecordAggregationQueryService);
            verifyNoInteractions(characterGrowthService);
        }
    }

    @Nested
    @DisplayName("팀 내부 회원 보드 조회")
    class GetTeamMemberView {

        @Test
        @DisplayName("현재 팀원 필터를 적용한 뒤 오늘 순위 계산")
        void ranksOnlyCurrentTeamMembers() {
            List<CohortMembershipView> memberships = memberships();
            givenStudentMembership();
            given(clock.instant()).willReturn(CALCULATED_AT);
            given(cohortMembershipQueryService.findActiveStudentMemberships(COHORT_ID))
                    .willReturn(memberships);
            given(currentTeamMembershipQueryService.findCurrentMemberships(
                    COHORT_ID,
                    membershipIds(memberships)
            )).willReturn(List.of(
                    new CurrentTeamMembershipView(100L, "선택 팀", 20L),
                    new CurrentTeamMembershipView(100L, "선택 팀", 21L),
                    new CurrentTeamMembershipView(200L, "다른 팀", 22L)
            ));
            given(studyRecordAggregationQueryService.getCurrentDurations(
                    List.of(20L, 21L),
                    CALCULATED_AT
            )).willReturn(List.of(
                    new MemberCurrentStudyDurationResult(20L, 7_200L, true),
                    new MemberCurrentStudyDurationResult(21L, 3_600L, false)
            ));
            givenDisplayNames(Set.of(LEADER_USER_ID, FIRST_TIE_USER_ID));

            TodayStudyRankingResult<MemberStudyRankingViewResult> result =
                    studyRankingQueryService.getTodayTeamMemberView(
                            USER_ID,
                            COHORT_ID,
                            100L,
                            new StudyRankingQuery(null)
                    );

            assertAll(
                    () -> assertEquals(2L, result.ranking().board().rankedMemberCount()),
                    () -> assertEquals(
                            List.of(1L, 2L),
                            result.ranking().board().entries().stream()
                                    .map(StudyRankingEntryResult::rank)
                                    .toList()
                    ),
                    () -> assertTrue(
                            result.ranking().board().entries().getFirst().timerRunning()
                    ),
                    () -> assertFalse(result.ranking().mine().ranked())
            );
        }

        @Test
        @DisplayName("선택 팀의 현재 소속이 없으면 오늘 빈 랭킹 반환")
        void returnsEmptyTodayRankingWithoutCurrentTeamMembers() {
            List<CohortMembershipView> memberships = memberships();
            givenStudentMembership();
            given(clock.instant()).willReturn(CALCULATED_AT);
            given(cohortMembershipQueryService.findActiveStudentMemberships(COHORT_ID))
                    .willReturn(memberships);
            given(currentTeamMembershipQueryService.findCurrentMemberships(
                    COHORT_ID,
                    membershipIds(memberships)
            )).willReturn(List.of(
                    new CurrentTeamMembershipView(200L, "다른 팀", 20L)
            ));

            TodayStudyRankingResult<MemberStudyRankingViewResult> result =
                    studyRankingQueryService.getTodayTeamMemberView(
                            USER_ID,
                            COHORT_ID,
                            100L,
                            new StudyRankingQuery(null)
                    );

            assertAll(
                    () -> assertEquals(0L, result.ranking().board().rankedMemberCount()),
                    () -> assertEquals(List.of(), result.ranking().board().entries()),
                    () -> assertFalse(result.ranking().mine().ranked())
            );
            verifyNoInteractions(studyRecordAggregationQueryService);
            verifyNoInteractions(characterGrowthService);
        }

        @Test
        @DisplayName("팀 밖 요청자는 내 순위 미참여 처리")
        void returnsUnrankedMineWhenRequesterIsOutsideTeam() {
            List<CohortMembershipView> memberships = memberships();
            LocalDate date = LocalDate.parse("2000-01-12");
            givenStudentMembership();
            given(clock.instant()).willReturn(CALCULATED_AT);
            given(cohortMembershipQueryService.findActiveStudentMemberships(COHORT_ID))
                    .willReturn(memberships);
            given(currentTeamMembershipQueryService.findCurrentMemberships(
                    COHORT_ID,
                    membershipIds(memberships)
            )).willReturn(List.of(
                    new CurrentTeamMembershipView(100L, "선택 팀", 20L),
                    new CurrentTeamMembershipView(200L, "다른 팀", 21L)
            ));
            given(studyRecordAggregationQueryService.getConfirmedDurations(
                    List.of(20L),
                    date,
                    date
            )).willReturn(List.of(new MemberStudyDurationResult(20L, 3_600L)));
            givenDisplayNames(Set.of(LEADER_USER_ID));

            HistoricalStudyRankingResult<MemberStudyRankingViewResult> result =
                    studyRankingQueryService.getHistoricalTeamMemberView(
                            USER_ID,
                            COHORT_ID,
                            100L,
                            StudyRankingPeriodSelection.daily(date),
                            new StudyRankingQuery(null)
                    );

            assertFalse(result.ranking().mine().ranked());
        }

        @Test
        @DisplayName("선택 팀의 현재 소속이 없으면 과거 빈 랭킹 반환")
        void returnsEmptyHistoricalRankingWithoutCurrentTeamMembers() {
            List<CohortMembershipView> memberships = memberships();
            LocalDate date = LocalDate.parse("2000-01-12");
            givenStudentMembership();
            given(clock.instant()).willReturn(CALCULATED_AT);
            given(cohortMembershipQueryService.findActiveStudentMemberships(COHORT_ID))
                    .willReturn(memberships);
            given(currentTeamMembershipQueryService.findCurrentMemberships(
                    COHORT_ID,
                    membershipIds(memberships)
            )).willReturn(List.of(
                    new CurrentTeamMembershipView(200L, "다른 팀", 20L)
            ));

            HistoricalStudyRankingResult<MemberStudyRankingViewResult> result =
                    studyRankingQueryService.getHistoricalTeamMemberView(
                            USER_ID,
                            COHORT_ID,
                            100L,
                            StudyRankingPeriodSelection.daily(date),
                            new StudyRankingQuery(null)
                    );

            assertAll(
                    () -> assertEquals(0L, result.ranking().board().rankedMemberCount()),
                    () -> assertEquals(List.of(), result.ranking().board().entries()),
                    () -> assertFalse(result.ranking().mine().ranked())
            );
            verifyNoInteractions(studyRecordAggregationQueryService);
            verifyNoInteractions(characterGrowthService);
        }
    }

    private void givenStudentMembership() {
        given(cohortAccessService.requireActiveStudentMembershipId(COHORT_ID, USER_ID))
                .willReturn(MEMBERSHIP_ID);
    }

    private void givenDisplayNames(Set<UUID> userIds) {
        given(characterGrowthService.findRepresentativeCharacters(userIds))
                .willReturn(List.of(
                        character(LEADER_USER_ID, 101L, "첫째"),
                        character(USER_ID, 102L, "나")
                ));
    }

    private List<CohortMembershipView> memberships() {
        return List.of(
                membership(20L, LEADER_USER_ID),
                membership(21L, FIRST_TIE_USER_ID),
                membership(22L, SECOND_TIE_USER_ID),
                membership(MEMBERSHIP_ID, USER_ID)
        );
    }

    private List<Long> membershipIds(List<CohortMembershipView> memberships) {
        return memberships.stream()
                .map(CohortMembershipView::membershipId)
                .toList();
    }

    private List<MemberCurrentStudyDurationResult> currentDurations() {
        return List.of(
                new MemberCurrentStudyDurationResult(20L, 7_200L, true),
                new MemberCurrentStudyDurationResult(21L, 3_600L, false),
                new MemberCurrentStudyDurationResult(22L, 3_600L, true),
                new MemberCurrentStudyDurationResult(MEMBERSHIP_ID, 1_800L, false)
        );
    }

    private List<MemberStudyDurationResult> confirmedDurations() {
        return List.of(
                new MemberStudyDurationResult(20L, 7_200L),
                new MemberStudyDurationResult(21L, 3_600L),
                new MemberStudyDurationResult(22L, 3_600L),
                new MemberStudyDurationResult(MEMBERSHIP_ID, 1_800L)
        );
    }

    private CohortMembershipView membership(Long membershipId, UUID userId) {
        return new CohortMembershipView(membershipId, COHORT_ID, userId);
    }

    private RepresentativeCharacterResult character(
            UUID userId,
            Long characterId,
            String displayName
    ) {
        return new RepresentativeCharacterResult(userId, characterId, displayName);
    }
}
