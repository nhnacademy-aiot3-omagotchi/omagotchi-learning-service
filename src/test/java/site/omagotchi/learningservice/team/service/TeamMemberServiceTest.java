package site.omagotchi.learningservice.team.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.team.application.TeamAccessSupport;
import site.omagotchi.learningservice.team.application.TeamErrorCode;
import site.omagotchi.learningservice.team.application.TeamMemberAddition;
import site.omagotchi.learningservice.team.application.TeamMemberService;
import site.omagotchi.learningservice.team.application.TeamMembership;
import site.omagotchi.learningservice.team.application.port.IdentityAccountClient;
import site.omagotchi.learningservice.team.application.port.IdentityAccountState;
import site.omagotchi.learningservice.team.application.port.TeamMemberRepository;
import site.omagotchi.learningservice.team.domain.Team;
import site.omagotchi.learningservice.team.domain.TeamMember;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TeamMemberServiceTest {

    @Mock
    TeamAccessSupport accessSupport;

    @Mock
    TeamMemberRepository teamMemberRepository;

    @Mock
    IdentityAccountClient identityAccountClient;

    @Mock
    TeamMemberAddition teamMemberAddition;

    @InjectMocks
    TeamMemberService teamMemberService;

    private final UUID userId = UUID.randomUUID();
    private final Team team = createTeamWithId();
    private final TeamMembership membership = new TeamMembership(10L, 1L, userId);
    private final TeamMember masterMember = createMemberWithId(1L, 1L, 10L, true);

    private static Team createTeamWithId() {
        Team team = Team.create(1L, "테스트");
        ReflectionTestUtils.setField(team, "id", 1L);
        return team;
    }

    private static TeamMember createMemberWithId(Long id, Long teamId, Long cohortMembershipId, boolean master) {
        TeamMember member = master
                ? TeamMember.master(teamId, cohortMembershipId)
                : TeamMember.member(teamId, cohortMembershipId);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    @Test
    @DisplayName("팀원이 남아있으면 마스터는 위임 없이 탈퇴할 수 없다.")
    void masterCannotLeaveWithoutDelegationWhenMembersRemain() {
        given(accessSupport.lockActiveTeam(1L)).willReturn(team);
        given(accessSupport.requireActiveMembership(1L, userId)).willReturn(membership);
        given(accessSupport.requireMembership(1L, 10L)).willReturn(masterMember);
        given(teamMemberRepository.countByTeamId(1L)).willReturn(3L);

        assertThatThrownBy(() -> teamMemberService.leave(1L, userId))
                .hasFieldOrPropertyWithValue("errorCode", TeamErrorCode.DELEGATION_REQUIRED);

        verify(teamMemberRepository, never()).delete(any());
    }

    @Test
    @DisplayName("혼자 남은 마스터가 탈퇴하면 팀도 해체된다.")
    void soleMasterLeavingDisbandsTeam() {
        given(accessSupport.lockActiveTeam(1L)).willReturn(team);
        given(accessSupport.requireActiveMembership(1L, userId)).willReturn(membership);
        given(accessSupport.requireMembership(1L, 10L)).willReturn(masterMember);
        given(teamMemberRepository.countByTeamId(1L)).willReturn(1L);

        teamMemberService.leave(1L, userId);

        verify(teamMemberRepository).delete(masterMember);
        assertThat(team.isDisbanded()).isTrue();
    }

    @Test
    @DisplayName("정상 요청이면 팀원이 추가된다.")
    void addsMemberOnValidRequest() {
        // Given: 현재 MASTER와 추가 가능한 활성 계정
        UUID targetUserId = UUID.randomUUID();

        given(accessSupport.requireActiveTeamCohortId(1L)).willReturn(1L);
        given(accessSupport.requireActiveMembership(1L, userId)).willReturn(membership);
        given(accessSupport.requireMaster(1L, 10L)).willReturn(masterMember);
        given(identityAccountClient.getState(targetUserId)).willReturn(IdentityAccountState.ACTIVE);

        // When: 팀원 추가
        teamMemberService.addMember(1L, targetUserId, userId);

        // Then: Identity 조회 전 접근 제어와 계정 조회 이후 쓰기 트랜잭션 실행
        InOrder order = inOrder(accessSupport, identityAccountClient, teamMemberAddition);
        order.verify(accessSupport).requireActiveTeamCohortId(1L);
        order.verify(accessSupport).requireActiveMembership(1L, userId);
        order.verify(accessSupport).requireMaster(1L, 10L);
        order.verify(identityAccountClient).getState(targetUserId);
        order.verify(teamMemberAddition).add(1L, targetUserId, userId);
    }

    @Test
    @DisplayName("마스터가 아닌 요청은 Identity 계정을 조회하지 않는다.")
    void nonMasterRequestDoesNotQueryIdentity() {
        // Given: 활성 멤버십은 있지만 MASTER가 아닌 요청자
        UUID targetUserId = UUID.randomUUID();

        given(accessSupport.requireActiveTeamCohortId(1L)).willReturn(1L);
        given(accessSupport.requireActiveMembership(1L, userId)).willReturn(membership);
        given(accessSupport.requireMaster(1L, 10L))
                .willThrow(new BusinessException(TeamErrorCode.MASTER_REQUIRED));

        // When & Then: MASTER 권한 오류 반환
        assertThatThrownBy(() -> teamMemberService.addMember(1L, targetUserId, userId))
                .hasFieldOrPropertyWithValue("errorCode", TeamErrorCode.MASTER_REQUIRED);

        // Then: Identity 조회와 쓰기 트랜잭션 실행 안 함
        verify(identityAccountClient, never()).getState(any());
        verify(teamMemberAddition, never()).add(any(), any(), any());
    }

    @Test
    @DisplayName("탈퇴한 계정은 팀원으로 추가할 수 없다.")
    void cannotAddWithdrawnAccountAsMember() {
        // Given: 현재 MASTER와 탈퇴한 대상 계정
        UUID targetUserId = UUID.randomUUID();

        given(accessSupport.requireActiveTeamCohortId(1L)).willReturn(1L);
        given(accessSupport.requireActiveMembership(1L, userId)).willReturn(membership);
        given(accessSupport.requireMaster(1L, 10L)).willReturn(masterMember);
        given(identityAccountClient.getState(targetUserId)).willReturn(IdentityAccountState.WITHDRAWN);

        // When & Then: 탈퇴 계정 오류 반환
        assertThatThrownBy(() -> teamMemberService.addMember(1L, targetUserId, userId))
                .hasFieldOrPropertyWithValue("errorCode", TeamErrorCode.ACCOUNT_WITHDRAWN);

        // Then: 쓰기 트랜잭션 실행 안 함
        verify(teamMemberAddition, never()).add(any(), any(), any());
    }

    @Test
    @DisplayName("존재하지 않는 계정은 팀원으로 추가할 수 없다.")
    void cannotAddNonExistentAccountAsMember() {
        // Given: 현재 MASTER와 존재하지 않는 대상 계정
        UUID targetUserId = UUID.randomUUID();

        given(accessSupport.requireActiveTeamCohortId(1L)).willReturn(1L);
        given(accessSupport.requireActiveMembership(1L, userId)).willReturn(membership);
        given(accessSupport.requireMaster(1L, 10L)).willReturn(masterMember);
        given(identityAccountClient.getState(targetUserId))
                .willThrow(new BusinessException(TeamErrorCode.ACCOUNT_NOT_FOUND));

        // When & Then: 계정 미존재 오류 반환
        assertThatThrownBy(() -> teamMemberService.addMember(1L, targetUserId, userId))
                .hasFieldOrPropertyWithValue("errorCode", TeamErrorCode.ACCOUNT_NOT_FOUND);

        // Then: 쓰기 트랜잭션 실행 안 함
        verify(teamMemberAddition, never()).add(any(), any(), any());
    }

    @Test
    @DisplayName("마스터가 일반 팀원을 제외하면 삭제된다.")
    void masterKickingMemberDeletesRow() {
        TeamMember normalMember = createMemberWithId(2L, 1L, 20L, false);

        given(accessSupport.lockActiveTeam(1L)).willReturn(team);
        given(accessSupport.requireActiveMembership(1L, userId)).willReturn(membership);
        given(accessSupport.requireMaster(1L, 10L)).willReturn(masterMember);
        given(teamMemberRepository.findById(2L)).willReturn(Optional.of(normalMember));

        teamMemberService.kickMember(1L, 2L, userId);

        verify(teamMemberRepository).delete(normalMember);
    }

    @Test
    @DisplayName("마스터 본인은 제외할 수 없다.")
    void masterCannotBeKicked() {
        given(accessSupport.lockActiveTeam(1L)).willReturn(team);
        given(accessSupport.requireActiveMembership(1L, userId)).willReturn(membership);
        given(accessSupport.requireMaster(1L, 10L)).willReturn(masterMember);
        given(teamMemberRepository.findById(1L)).willReturn(Optional.of(masterMember));

        assertThatThrownBy(() -> teamMemberService.kickMember(1L, 1L, userId))
                .hasFieldOrPropertyWithValue("errorCode", TeamErrorCode.MASTER_CANNOT_BE_KICKED);

        verify(teamMemberRepository, never()).delete(any());
    }

    @Test
    @DisplayName("존재하지 않는 팀원을 제외할 수 없다.")
    void cannotKickNonExistentMember() {
        given(accessSupport.lockActiveTeam(1L)).willReturn(team);
        given(accessSupport.requireActiveMembership(1L, userId)).willReturn(membership);
        given(accessSupport.requireMaster(1L, 10L)).willReturn(masterMember);
        given(teamMemberRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamMemberService.kickMember(1L, 999L, userId))
                .hasFieldOrPropertyWithValue("errorCode", TeamErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 팀 소속의 memberId를 지정하면 제외할 수 없다.")
    void cannotKickMemberIdFromAnotherTeam() {
        TeamMember otherTeamMember = createMemberWithId(5L, 2L, 30L, false);

        given(accessSupport.lockActiveTeam(1L)).willReturn(team);
        given(accessSupport.requireActiveMembership(1L, userId)).willReturn(membership);
        given(accessSupport.requireMaster(1L, 10L)).willReturn(masterMember);
        given(teamMemberRepository.findById(5L)).willReturn(Optional.of(otherTeamMember));

        assertThatThrownBy(() -> teamMemberService.kickMember(1L, 5L, userId))
                .hasFieldOrPropertyWithValue("errorCode", TeamErrorCode.MEMBER_NOT_FOUND);

        verify(teamMemberRepository, never()).delete(any());
    }
}
