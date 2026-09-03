package site.omagotchi.learningservice.team.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.cohort.application.result.CohortMembershipView;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.team.application.port.IdentityAccountClient;
import site.omagotchi.learningservice.team.application.port.IdentityAccountState;
import site.omagotchi.learningservice.team.application.port.IdentityAccountView;
import site.omagotchi.learningservice.team.application.port.TeamMemberRepository;
import site.omagotchi.learningservice.team.application.result.TeamMemberCandidateResult;
import site.omagotchi.learningservice.team.application.result.TeamMemberCandidateStatus;
import site.omagotchi.learningservice.team.domain.Team;
import site.omagotchi.learningservice.team.domain.TeamMember;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TeamMemberCandidateQueryServiceTest {

    private static final Long TEAM_ID = 100L;
    private static final Long OTHER_TEAM_ID = 200L;
    private static final Long COHORT_ID = 3L;

    @Mock
    private TeamAccessSupport accessSupport;

    @Mock
    private CohortMembershipQueryService cohortMembershipQueryService;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private IdentityAccountClient identityAccountClient;

    @InjectMocks
    private TeamMemberCandidateQueryService service;

    @Test
    @DisplayName("후보의 현재 팀 소속에 따라 세 가지 추가 가능 상태를 반환한다.")
    void returnsCandidateStatusFromCurrentTeamMembership() {
        UUID requesterId = UUID.randomUUID();
        UUID currentTeamUserId = UUID.randomUUID();
        UUID anotherTeamUserId = UUID.randomUUID();
        UUID availableUserId = UUID.randomUUID();
        UUID withdrawnUserId = UUID.randomUUID();
        List<CohortMembershipView> memberships = List.of(
                membership(10L, requesterId),
                membership(11L, currentTeamUserId),
                membership(12L, anotherTeamUserId),
                membership(13L, availableUserId),
                membership(14L, withdrawnUserId)
        );
        allowMaster(requesterId, 10L);
        given(cohortMembershipQueryService.findActiveMemberships(COHORT_ID))
                .willReturn(memberships);
        given(teamMemberRepository.findByCohortMembershipIdIn(
                List.of(10L, 11L, 12L, 13L, 14L)))
                .willReturn(List.of(
                        TeamMember.master(TEAM_ID, 10L),
                        TeamMember.member(TEAM_ID, 11L),
                        TeamMember.member(OTHER_TEAM_ID, 12L)
                ));
        given(identityAccountClient.search(eq("학생"), any()))
                .willReturn(List.of(
                        account(currentTeamUserId, "현재 팀원", IdentityAccountState.ACTIVE),
                        account(anotherTeamUserId, "다른 팀원", IdentityAccountState.ACTIVE),
                        account(availableUserId, "추가 가능", IdentityAccountState.ACTIVE),
                        account(withdrawnUserId, "탈퇴 계정", IdentityAccountState.WITHDRAWN)
                ));

        List<TeamMemberCandidateResult> result = service.search(TEAM_ID, "  학생  ", requesterId);

        assertThat(result)
                .extracting(TeamMemberCandidateResult::displayName, TeamMemberCandidateResult::status)
                .containsExactly(
                        tuple("현재 팀원", TeamMemberCandidateStatus.ALREADY_IN_THIS_TEAM),
                        tuple("다른 팀원", TeamMemberCandidateStatus.IN_ANOTHER_TEAM),
                        tuple("추가 가능", TeamMemberCandidateStatus.AVAILABLE)
                );
    }

    @Test
    @DisplayName("마스터의 검색어가 공백이거나 100자를 넘으면 후보 조회 전에 거부한다.")
    void rejectsInvalidQueryBeforeCandidateLookup() {
        UUID requesterId = UUID.randomUUID();
        allowMaster(requesterId, 10L);

        assertThatThrownBy(() -> service.search(TEAM_ID, "   ", requesterId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", TeamErrorCode.INVALID_MEMBER_QUERY);
        assertThatThrownBy(() -> service.search(TEAM_ID, "가".repeat(101), requesterId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", TeamErrorCode.INVALID_MEMBER_QUERY);

        verify(cohortMembershipQueryService, never()).findActiveMemberships(COHORT_ID);
        verify(identityAccountClient, never()).search(any(), any());
    }

    @Test
    @DisplayName("마스터가 아니면 Identity 후보 검색을 호출하지 않는다.")
    void rejectsNonMasterBeforeIdentitySearch() {
        UUID requesterId = UUID.randomUUID();
        Team team = team();
        given(accessSupport.loadActiveTeam(TEAM_ID)).willReturn(team);
        given(accessSupport.requireActiveMembership(COHORT_ID, requesterId))
                .willReturn(new TeamMembership(10L, COHORT_ID, requesterId));
        given(accessSupport.requireMaster(TEAM_ID, 10L))
                .willThrow(new BusinessException(TeamErrorCode.MASTER_REQUIRED));

        assertThatThrownBy(() -> service.search(TEAM_ID, "학생", requesterId))
                .hasFieldOrPropertyWithValue("errorCode", TeamErrorCode.MASTER_REQUIRED);

        verify(identityAccountClient, never()).search(any(), any());
    }

    @Test
    @DisplayName("팀원 후보 검색 결과는 최대 20명까지만 반환한다.")
    void limitsCandidateResultsToTwenty() {
        UUID requesterId = UUID.randomUUID();
        List<CohortMembershipView> memberships = new ArrayList<>();
        memberships.add(membership(10L, requesterId));
        IntStream.range(0, 21).forEach(index ->
                memberships.add(membership(100L + index, UUID.randomUUID())));
        allowMaster(requesterId, 10L);
        given(cohortMembershipQueryService.findActiveMemberships(COHORT_ID))
                .willReturn(memberships);
        given(teamMemberRepository.findByCohortMembershipIdIn(any())).willReturn(List.of());
        given(identityAccountClient.search(any(), any())).willReturn(
                memberships.stream()
                        .skip(1)
                        .map(membership -> account(
                                membership.userId(), "후보", IdentityAccountState.ACTIVE))
                        .toList());

        assertThat(service.search(TEAM_ID, "후보", requesterId)).hasSize(20);
    }

    private void allowMaster(UUID requesterId, Long membershipId) {
        given(accessSupport.loadActiveTeam(TEAM_ID)).willReturn(team());
        given(accessSupport.requireActiveMembership(COHORT_ID, requesterId))
                .willReturn(new TeamMembership(membershipId, COHORT_ID, requesterId));
        given(accessSupport.requireMaster(TEAM_ID, membershipId))
                .willReturn(TeamMember.master(TEAM_ID, membershipId));
    }

    private static Team team() {
        Team team = Team.create(COHORT_ID, "테스트 팀");
        ReflectionTestUtils.setField(team, "id", TEAM_ID);
        return team;
    }

    private static CohortMembershipView membership(Long membershipId, UUID userId) {
        return new CohortMembershipView(membershipId, COHORT_ID, userId);
    }

    private static IdentityAccountView account(
            UUID userId,
            String displayName,
            IdentityAccountState state
    ) {
        return new IdentityAccountView(
                userId,
                displayName,
                displayName + "@example.com",
                state
        );
    }
}
