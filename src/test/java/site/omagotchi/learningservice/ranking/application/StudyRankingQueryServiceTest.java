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
import site.omagotchi.learningservice.cohort.domain.CohortErrorCode;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.gamification.application.CharacterGrowthService;
import site.omagotchi.learningservice.gamification.application.result.RepresentativeCharacterResult;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.ranking.application.port.StudyRankingRepository;
import site.omagotchi.learningservice.ranking.application.port.StudyRankingRepository.RankedStudyMember;
import site.omagotchi.learningservice.ranking.application.port.StudyRankingRepository.StudyRankingRows;
import site.omagotchi.learningservice.ranking.application.query.StudyRankingPeriod;
import site.omagotchi.learningservice.ranking.application.query.StudyRankingQuery;
import site.omagotchi.learningservice.ranking.application.query.StudyRankingWindow;
import site.omagotchi.learningservice.ranking.application.result.MemberStudyRankingViewResult;
import site.omagotchi.learningservice.ranking.application.result.MyStudyRankingResult;
import site.omagotchi.learningservice.ranking.application.result.StudyRankingBoardResult;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("학습 랭킹 조회")
@ExtendWith(MockitoExtension.class)
class StudyRankingQueryServiceTest {

    private static final UUID USER_ID = new UUID(0L, 1L);
    private static final UUID LEADER_USER_ID = new UUID(0L, 2L);
    private static final UUID SECOND_USER_ID = new UUID(0L, 3L);
    private static final Long COHORT_ID = 10L;
    private static final Long MEMBERSHIP_ID = 11L;
    private static final Instant CALCULATED_AT = Instant.parse("2000-01-12T20:00:00Z");
    @Mock
    private CohortAccessService cohortAccessService;

    @Mock
    private StudyRankingRepository studyRankingRepository;

    @Mock
    private CharacterGrowthService characterGrowthService;

    @Mock
    private Clock clock;

    @Mock
    private CohortMembership membership;

    @InjectMocks
    private StudyRankingQueryService studyRankingQueryService;

    @Nested
    @DisplayName("회원 보드와 내 순위 조회")
    class GetMemberView {

        @Test
        @DisplayName("하나의 랭킹 조회 결과로 보드와 내 순위 정상 처리")
        void returnsBoardAndMineFromSameRankingRead() {
            StudyRankingQuery query = new StudyRankingQuery(
                    StudyRankingPeriod.DAILY,
                    2
            );
            StudyRankingWindow window = dailyWindow();
            RankedStudyMember leader = rankedMember(20L, LEADER_USER_ID, 1L, 7_200L);
            RankedStudyMember second = rankedMember(21L, SECOND_USER_ID, 2L, 3_600L);
            RankedStudyMember mine = rankedMember(MEMBERSHIP_ID, USER_ID, 3L, 1_800L);
            StudyRankingRows rows = new StudyRankingRows(
                    3L,
                    List.of(leader, second),
                    Optional.of(mine)
            );
            given(cohortAccessService.requireActiveMembership(COHORT_ID, USER_ID))
                    .willReturn(membership);
            given(membership.getRole()).willReturn(CohortMembershipRole.STUDENT);
            given(membership.getId()).willReturn(MEMBERSHIP_ID);
            given(clock.instant()).willReturn(CALCULATED_AT);
            given(studyRankingRepository.findBoardAndMember(
                    window,
                    2,
                    COHORT_ID,
                    MEMBERSHIP_ID
            )).willReturn(rows);
            given(characterGrowthService.findRepresentativeCharacters(
                    Set.of(LEADER_USER_ID, SECOND_USER_ID, USER_ID)
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
                    () -> assertEquals(3L, result.board().rankedMemberCount()),
                    () -> assertEquals(2, result.board().entries().size()),
                    () -> assertEquals("첫째", result.board().entries().getFirst().displayName()),
                    () -> assertNull(result.board().entries().getLast().displayName()),
                    () -> assertTrue(result.mine().ranked()),
                    () -> assertEquals(3L, result.mine().ranking().orElseThrow().rank()),
                    () -> assertEquals("나", result.mine().ranking().orElseThrow().displayName())
            );
            InOrder inOrder = inOrder(
                    cohortAccessService,
                    clock,
                    studyRankingRepository,
                    characterGrowthService
            );
            inOrder.verify(cohortAccessService).requireActiveMembership(COHORT_ID, USER_ID);
            inOrder.verify(clock).instant();
            inOrder.verify(studyRankingRepository).findBoardAndMember(
                    window,
                    2,
                    COHORT_ID,
                    MEMBERSHIP_ID
            );
            inOrder.verify(characterGrowthService).findRepresentativeCharacters(
                    Set.of(LEADER_USER_ID, SECOND_USER_ID, USER_ID)
            );
            verify(studyRankingRepository, never()).findMember(
                    window,
                    COHORT_ID,
                    MEMBERSHIP_ID
            );
        }

        @Test
        @DisplayName("수강생 역할 없음은 조회 전 예외")
        void rejectsNonStudentBeforeReadingRanking() {
            given(cohortAccessService.requireActiveMembership(COHORT_ID, USER_ID))
                    .willReturn(membership);
            given(membership.getRole()).willReturn(CohortMembershipRole.MANAGER);

            BusinessException exception = assertThrows(
                    BusinessException.class,
                    () -> studyRankingQueryService.getMemberView(
                            USER_ID,
                            COHORT_ID,
                            new StudyRankingQuery(StudyRankingPeriod.DAILY, null)
                    )
            );

            assertEquals(CohortErrorCode.COHORT_ACCESS_DENIED, exception.getErrorCode());
            verifyNoInteractions(clock, studyRankingRepository, characterGrowthService);
        }
    }

    @Nested
    @DisplayName("내 순위 조회")
    class GetMine {

        @Test
        @DisplayName("보드 조회 없이 내 순위만 정상 처리")
        void returnsOnlyMine() {
            StudyRankingWindow window = dailyWindow();
            RankedStudyMember mine = rankedMember(MEMBERSHIP_ID, USER_ID, 7L, 600L);
            StudyRankingRows rows = new StudyRankingRows(
                    20L,
                    List.of(),
                    Optional.of(mine)
            );
            given(cohortAccessService.requireActiveMembership(COHORT_ID, USER_ID))
                    .willReturn(membership);
            given(membership.getRole()).willReturn(CohortMembershipRole.STUDENT);
            given(membership.getId()).willReturn(MEMBERSHIP_ID);
            given(clock.instant()).willReturn(CALCULATED_AT);
            given(studyRankingRepository.findMember(window, COHORT_ID, MEMBERSHIP_ID))
                    .willReturn(rows);
            given(characterGrowthService.findRepresentativeCharacters(Set.of(USER_ID)))
                    .willReturn(List.of(character(USER_ID, 101L, "나")));

            MyStudyRankingResult result = studyRankingQueryService.getMine(
                    USER_ID,
                    COHORT_ID,
                    StudyRankingPeriod.DAILY
            );

            assertAll(
                    () -> assertEquals(20L, result.rankedMemberCount()),
                    () -> assertTrue(result.ranked()),
                    () -> assertEquals(7L, result.ranking().orElseThrow().rank()),
                    () -> assertEquals("나", result.ranking().orElseThrow().displayName())
            );
            verify(studyRankingRepository, never()).findBoardAndMember(
                    window,
                    StudyRankingQuery.DEFAULT_MAX_RANK,
                    COHORT_ID,
                    MEMBERSHIP_ID
            );
        }

        @Test
        @DisplayName("공부 기록 없음은 미랭크 정상 처리")
        void returnsUnrankedWhenNoRecordExists() {
            StudyRankingWindow window = dailyWindow();
            given(cohortAccessService.requireActiveMembership(COHORT_ID, USER_ID))
                    .willReturn(membership);
            given(membership.getRole()).willReturn(CohortMembershipRole.STUDENT);
            given(membership.getId()).willReturn(MEMBERSHIP_ID);
            given(clock.instant()).willReturn(CALCULATED_AT);
            given(studyRankingRepository.findMember(window, COHORT_ID, MEMBERSHIP_ID))
                    .willReturn(new StudyRankingRows(2L, List.of(), Optional.empty()));

            MyStudyRankingResult result = studyRankingQueryService.getMine(
                    USER_ID,
                    COHORT_ID,
                    StudyRankingPeriod.DAILY
            );

            assertAll(
                    () -> assertEquals(2L, result.rankedMemberCount()),
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
        @DisplayName("최대 순위 기본값 정상 처리")
        void usesDefaultMaxRank() {
            StudyRankingWindow window = dailyWindow();
            given(clock.instant()).willReturn(CALCULATED_AT);
            given(studyRankingRepository.findBoard(
                    window,
                    StudyRankingQuery.DEFAULT_MAX_RANK,
                    COHORT_ID
            )).willReturn(new StudyRankingRows(0L, List.of(), Optional.empty()));

            StudyRankingBoardResult result = studyRankingQueryService.getManagerBoard(
                    USER_ID,
                    COHORT_ID,
                    new StudyRankingQuery(StudyRankingPeriod.DAILY, null)
            );

            assertTrue(result.entries().isEmpty());
            InOrder inOrder = inOrder(
                    cohortAccessService,
                    clock,
                    studyRankingRepository
            );
            inOrder.verify(cohortAccessService).requireManager(COHORT_ID, USER_ID);
            inOrder.verify(clock).instant();
            inOrder.verify(studyRankingRepository).findBoard(
                    window,
                    StudyRankingQuery.DEFAULT_MAX_RANK,
                    COHORT_ID
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
            verifyNoInteractions(clock, studyRankingRepository, characterGrowthService);
        }
    }

    private StudyRankingWindow dailyWindow() {
        return StudyRankingWindow.resolve(
                StudyRankingPeriod.DAILY,
                CALCULATED_AT
        );
    }

    private RankedStudyMember rankedMember(
            Long membershipId,
            UUID userId,
            long rank,
            long studySeconds
    ) {
        return new RankedStudyMember(membershipId, userId, rank, studySeconds);
    }

    private RepresentativeCharacterResult character(
            UUID userId,
            Long characterId,
            String displayName
    ) {
        return new RepresentativeCharacterResult(userId, characterId, displayName);
    }
}
