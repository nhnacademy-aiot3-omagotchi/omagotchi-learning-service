package site.omagotchi.learningservice.ranking.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
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
import site.omagotchi.learningservice.ranking.application.query.StudyRankingPeriod;
import site.omagotchi.learningservice.ranking.application.query.StudyRankingQuery;
import site.omagotchi.learningservice.ranking.application.query.StudyRankingWindow;
import site.omagotchi.learningservice.ranking.application.result.MemberStudyRankingViewResult;
import site.omagotchi.learningservice.ranking.application.result.MyStudyRankingResult;
import site.omagotchi.learningservice.ranking.application.result.StudyRankingBoardResult;
import site.omagotchi.learningservice.ranking.application.result.StudyRankingEntryResult;
import site.omagotchi.learningservice.study.application.StudyRecordAggregationQueryService;
import site.omagotchi.learningservice.study.application.result.MemberStudyDurationResult;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
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

    @Mock
    private CohortAccessService cohortAccessService;

    @Mock
    private CohortMembershipQueryService cohortMembershipQueryService;

    @Mock
    private StudyRecordAggregationQueryService studyRecordAggregationQueryService;

    @Mock
    private CharacterGrowthService characterGrowthService;

    @Mock
    private Clock clock;

    @InjectMocks
    private StudyRankingQueryService studyRankingQueryService;

    @Nested
    @DisplayName("회원 보드와 내 순위 조회")
    class GetMemberView {

        @Test
        @DisplayName("feature 조회 결과를 조립해 동점 경계와 내 순위를 함께 반환")
        void returnsBoardAndMineFromSameRankingSet() {
            StudyRankingQuery query = new StudyRankingQuery(StudyRankingPeriod.DAILY, 2);
            List<CohortMembershipView> memberships = memberships();
            given(cohortAccessService.requireActiveStudentMembershipId(COHORT_ID, USER_ID))
                    .willReturn(MEMBERSHIP_ID);
            given(clock.instant()).willReturn(CALCULATED_AT);
            given(cohortMembershipQueryService.findActiveStudentMemberships(COHORT_ID))
                    .willReturn(memberships);
            givenConfirmedDurations(memberships, durations());
            given(characterGrowthService.findRepresentativeCharacters(
                    Set.of(
                            LEADER_USER_ID,
                            FIRST_TIE_USER_ID,
                            SECOND_TIE_USER_ID,
                            USER_ID
                    )
            )).willReturn(List.of(
                    character(LEADER_USER_ID, 101L, "첫째"),
                    character(USER_ID, 102L, "나")
            ));

            MemberStudyRankingViewResult result = studyRankingQueryService.getMemberView(
                    USER_ID,
                    COHORT_ID,
                    query
            );

            assertAll(
                    () -> assertEquals(4L, result.board().rankedMemberCount()),
                    () -> assertEquals(3, result.board().entries().size()),
                    () -> assertEquals(List.of(1L, 2L, 2L), result.board().entries().stream()
                            .map(StudyRankingEntryResult::rank)
                            .toList()),
                    () -> assertEquals("첫째", result.board().entries().getFirst().displayName()),
                    () -> assertNull(result.board().entries().getLast().displayName()),
                    () -> assertTrue(result.mine().ranked()),
                    () -> assertEquals(4L, result.mine().ranking().orElseThrow().rank()),
                    () -> assertEquals("나", result.mine().ranking().orElseThrow().displayName())
            );
            InOrder inOrder = inOrder(
                    cohortAccessService,
                    clock,
                    cohortMembershipQueryService,
                    studyRecordAggregationQueryService,
                    characterGrowthService
            );
            inOrder.verify(cohortAccessService)
                    .requireActiveStudentMembershipId(COHORT_ID, USER_ID);
            inOrder.verify(clock).instant();
            inOrder.verify(cohortMembershipQueryService)
                    .findActiveStudentMemberships(COHORT_ID);
            inOrder.verify(studyRecordAggregationQueryService).getConfirmedDurations(
                    membershipIds(memberships),
                    dailyWindow().startDate(),
                    dailyWindow().endDate()
            );
            inOrder.verify(characterGrowthService).findRepresentativeCharacters(
                    Set.of(
                            LEADER_USER_ID,
                            FIRST_TIE_USER_ID,
                            SECOND_TIE_USER_ID,
                            USER_ID
                    )
            );
        }

        @Test
        @DisplayName("활성 학생 권한이 없으면 feature 조회 전에 예외")
        void rejectsNonStudentBeforeReadingRanking() {
            willThrow(new BusinessException(CohortErrorCode.COHORT_ACCESS_DENIED))
                    .given(cohortAccessService)
                    .requireActiveStudentMembershipId(COHORT_ID, USER_ID);

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> studyRankingQueryService.getMemberView(
                            USER_ID,
                            COHORT_ID,
                            new StudyRankingQuery(StudyRankingPeriod.DAILY, null)
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
    @DisplayName("내 순위 조회")
    class GetMine {

        @Test
        @DisplayName("보드 표시명 없이 내 순위만 반환")
        void returnsOnlyMine() {
            List<CohortMembershipView> memberships = memberships();
            given(cohortAccessService.requireActiveStudentMembershipId(COHORT_ID, USER_ID))
                    .willReturn(MEMBERSHIP_ID);
            given(clock.instant()).willReturn(CALCULATED_AT);
            given(cohortMembershipQueryService.findActiveStudentMemberships(COHORT_ID))
                    .willReturn(memberships);
            givenConfirmedDurations(memberships, durations());
            given(characterGrowthService.findRepresentativeCharacters(Set.of(USER_ID)))
                    .willReturn(List.of(character(USER_ID, 101L, "나")));

            MyStudyRankingResult result = studyRankingQueryService.getMine(
                    USER_ID,
                    COHORT_ID,
                    StudyRankingPeriod.DAILY
            );

            assertAll(
                    () -> assertEquals(4L, result.rankedMemberCount()),
                    () -> assertTrue(result.ranked()),
                    () -> assertEquals(4L, result.ranking().orElseThrow().rank()),
                    () -> assertEquals("나", result.ranking().orElseThrow().displayName())
            );
        }

        @Test
        @DisplayName("공부 기록 없음은 미랭크")
        void returnsUnrankedWhenNoRecordExists() {
            List<CohortMembershipView> memberships = memberships();
            given(cohortAccessService.requireActiveStudentMembershipId(COHORT_ID, USER_ID))
                    .willReturn(MEMBERSHIP_ID);
            given(clock.instant()).willReturn(CALCULATED_AT);
            given(cohortMembershipQueryService.findActiveStudentMemberships(COHORT_ID))
                    .willReturn(memberships);
            givenConfirmedDurations(
                    memberships,
                    durations().stream()
                            .filter(duration -> !duration.cohortMembershipId().equals(MEMBERSHIP_ID))
                            .toList()
            );

            MyStudyRankingResult result = studyRankingQueryService.getMine(
                    USER_ID,
                    COHORT_ID,
                    StudyRankingPeriod.DAILY
            );

            assertAll(
                    () -> assertEquals(3L, result.rankedMemberCount()),
                    () -> assertFalse(result.ranked()),
                    () -> assertTrue(result.ranking().isEmpty())
            );
            verifyNoInteractions(characterGrowthService);
        }
    }

    @Nested
    @DisplayName("관리자 보드 조회")
    class GetManagerBoard {

        @Test
        @DisplayName("최대 순위 기본값이 인원보다 크면 전체 보드 반환")
        void usesDefaultMaxRank() {
            List<CohortMembershipView> memberships = memberships();
            given(clock.instant()).willReturn(CALCULATED_AT);
            given(cohortMembershipQueryService.findActiveStudentMemberships(COHORT_ID))
                    .willReturn(memberships);
            givenConfirmedDurations(memberships, durations());

            StudyRankingBoardResult result = studyRankingQueryService.getManagerBoard(
                    USER_ID,
                    COHORT_ID,
                    new StudyRankingQuery(StudyRankingPeriod.DAILY, null)
            );

            assertEquals(4, result.entries().size());
            InOrder inOrder = inOrder(
                    cohortAccessService,
                    clock,
                    cohortMembershipQueryService,
                    studyRecordAggregationQueryService
            );
            inOrder.verify(cohortAccessService).requireManager(COHORT_ID, USER_ID);
            inOrder.verify(clock).instant();
            inOrder.verify(cohortMembershipQueryService)
                    .findActiveStudentMemberships(COHORT_ID);
            inOrder.verify(studyRecordAggregationQueryService).getConfirmedDurations(
                    membershipIds(memberships),
                    dailyWindow().startDate(),
                    dailyWindow().endDate()
            );
        }

        @Test
        @DisplayName("관리자 권한 없음은 조회 전 예외")
        void rejectsNonManagerBeforeReadingRanking() {
            willThrow(new BusinessException(CohortErrorCode.COHORT_MANAGER_REQUIRED))
                    .given(cohortAccessService)
                    .requireManager(COHORT_ID, USER_ID);

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> studyRankingQueryService.getManagerBoard(
                            USER_ID,
                            COHORT_ID,
                            new StudyRankingQuery(StudyRankingPeriod.DAILY, null)
                    )
            );

            assertEquals(CohortErrorCode.COHORT_MANAGER_REQUIRED, exception.getErrorCode());
            verifyNoInteractions(
                    clock,
                    cohortMembershipQueryService,
                    studyRecordAggregationQueryService,
                    characterGrowthService
            );
        }
    }

    private void givenConfirmedDurations(
            List<CohortMembershipView> memberships,
            List<MemberStudyDurationResult> durations
    ) {
        given(studyRecordAggregationQueryService.getConfirmedDurations(
                membershipIds(memberships),
                dailyWindow().startDate(),
                dailyWindow().endDate()
        )).willReturn(durations);
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
        return memberships.stream().map(CohortMembershipView::membershipId).toList();
    }

    private List<MemberStudyDurationResult> durations() {
        return List.of(
                duration(20L, 7_200L),
                duration(21L, 3_600L),
                duration(22L, 3_600L),
                duration(MEMBERSHIP_ID, 1_800L)
        );
    }

    private CohortMembershipView membership(Long membershipId, UUID userId) {
        return new CohortMembershipView(membershipId, COHORT_ID, userId);
    }

    private MemberStudyDurationResult duration(Long membershipId, long studySeconds) {
        return new MemberStudyDurationResult(membershipId, studySeconds);
    }

    private StudyRankingWindow dailyWindow() {
        return StudyRankingWindow.resolve(StudyRankingPeriod.DAILY, CALCULATED_AT);
    }

    private RepresentativeCharacterResult character(
            UUID userId,
            Long characterId,
            String displayName
    ) {
        return new RepresentativeCharacterResult(userId, characterId, displayName);
    }
}
