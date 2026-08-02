package site.omagotchi.learningservice.team.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import site.omagotchi.learningservice.team.application.port.AccountReader;
import site.omagotchi.learningservice.team.application.port.MembershipReader;
import site.omagotchi.learningservice.team.application.TeamAccessSupport;
import site.omagotchi.learningservice.team.application.TeamMemberService;
import site.omagotchi.learningservice.team.application.TeamMembership;
import site.omagotchi.learningservice.team.application.dto.command.AddTeamMemberRequest;
import site.omagotchi.learningservice.team.domain.Team;
import site.omagotchi.learningservice.team.application.TeamErrorCode;
import site.omagotchi.learningservice.team.domain.TeamMember;
import site.omagotchi.learningservice.team.application.port.TeamMemberRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TeamMemberServiceTest {

    @Mock
    TeamAccessSupport accessSupport;

    @Mock
    TeamMemberRepository teamMemberRepository;

    @Mock
    MembershipReader membershipReader;

    @Mock
    AccountReader accountReader;

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
    void test1() {
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
    void test2() {
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
    void test3() {
        UUID targetUserId = UUID.randomUUID();
        TeamMembership targetMembership = new TeamMembership(20L, 1L, targetUserId);

        given(accessSupport.requireActiveTeamCohortId(1L)).willReturn(1L);
        given(accessSupport.requireActiveMembership(1L, userId)).willReturn(membership);
        given(accessSupport.requireMaster(1L, 10L)).willReturn(masterMember);
        given(accountReader.findState(targetUserId)).willReturn(AccountReader.AccountState.ACTIVE);
        given(membershipReader.findActive(1L, targetUserId)).willReturn(Optional.of(targetMembership));
        given(accessSupport.lockActiveTeam(1L)).willReturn(team);
        given(teamMemberRepository.countByTeamId(1L)).willReturn(3L);

        teamMemberService.addMember(1L, new AddTeamMemberRequest(targetUserId), userId);

        ArgumentCaptor<TeamMember> captor = ArgumentCaptor.forClass(TeamMember.class);
        verify(teamMemberRepository).save(captor.capture());
        assertThat(captor.getValue().getCohortMembershipId()).isEqualTo(20L);
        assertThat(captor.getValue().isMaster()).isFalse();
    }

    @Test
    @DisplayName("대상이 팀의 기수에 속해 있지 않으면 거부한다.")
    void test4() {
        UUID targetUserId = UUID.randomUUID();

        given(accessSupport.requireActiveTeamCohortId(1L)).willReturn(1L);
        given(accessSupport.requireActiveMembership(1L, userId)).willReturn(membership);
        given(accessSupport.requireMaster(1L, 10L)).willReturn(masterMember);
        given(accountReader.findState(targetUserId)).willReturn(AccountReader.AccountState.ACTIVE);
        given(membershipReader.findActive(1L, targetUserId)).willReturn(Optional.empty());

        AddTeamMemberRequest request = new AddTeamMemberRequest(targetUserId);

        assertThatThrownBy(() -> teamMemberService.addMember(1L, request, userId))
                .hasFieldOrPropertyWithValue("errorCode", TeamErrorCode.TARGET_NOT_IN_COHORT);

        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("탈퇴한 계정은 팀원으로 추가할 수 없다.")
    void test5() {
        UUID targetUserId = UUID.randomUUID();

        given(accessSupport.requireActiveTeamCohortId(1L)).willReturn(1L);
        given(accessSupport.requireActiveMembership(1L, userId)).willReturn(membership);
        given(accessSupport.requireMaster(1L, 10L)).willReturn(masterMember);
        given(accountReader.findState(targetUserId)).willReturn(AccountReader.AccountState.WITHDRAWN);

        AddTeamMemberRequest request = new AddTeamMemberRequest(targetUserId);

        assertThatThrownBy(() -> teamMemberService.addMember(1L, request, userId))
                .hasFieldOrPropertyWithValue("errorCode", TeamErrorCode.ACCOUNT_WITHDRAWN);

        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("존재하지 않는 계정은 팀원으로 추가할 수 없다.")
    void test6() {
        UUID targetUserId = UUID.randomUUID();

        given(accessSupport.requireActiveTeamCohortId(1L)).willReturn(1L);
        given(accessSupport.requireActiveMembership(1L, userId)).willReturn(membership);
        given(accessSupport.requireMaster(1L, 10L)).willReturn(masterMember);
        given(accountReader.findState(targetUserId)).willReturn(AccountReader.AccountState.NOT_FOUND);

        AddTeamMemberRequest request = new AddTeamMemberRequest(targetUserId);

        assertThatThrownBy(() -> teamMemberService.addMember(1L, request, userId))
                .hasFieldOrPropertyWithValue("errorCode", TeamErrorCode.ACCOUNT_NOT_FOUND);

        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("정원이 가득 차면 팀원을 추가할 수 없다.")
    void test7() {
        UUID targetUserId = UUID.randomUUID();
        TeamMembership targetMembership = new TeamMembership(20L, 1L, targetUserId);

        given(accessSupport.requireActiveTeamCohortId(1L)).willReturn(1L);
        given(accessSupport.requireActiveMembership(1L, userId)).willReturn(membership);
        given(accessSupport.requireMaster(1L, 10L)).willReturn(masterMember);
        given(accountReader.findState(targetUserId)).willReturn(AccountReader.AccountState.ACTIVE);
        given(membershipReader.findActive(1L, targetUserId)).willReturn(Optional.of(targetMembership));
        given(accessSupport.lockActiveTeam(1L)).willReturn(team);
        given(teamMemberRepository.countByTeamId(1L)).willReturn(8L);

        AddTeamMemberRequest request = new AddTeamMemberRequest(targetUserId);

        assertThatThrownBy(() -> teamMemberService.addMember(1L, request, userId))
                .hasFieldOrPropertyWithValue("errorCode", TeamErrorCode.CAPACITY_EXCEEDED);

        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("마스터가 일반 팀원을 제외하면 삭제된다.")
    void test8() {
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
    void test9() {
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
    void test10() {
        given(accessSupport.lockActiveTeam(1L)).willReturn(team);
        given(accessSupport.requireActiveMembership(1L, userId)).willReturn(membership);
        given(accessSupport.requireMaster(1L, 10L)).willReturn(masterMember);
        given(teamMemberRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamMemberService.kickMember(1L, 999L, userId))
                .hasFieldOrPropertyWithValue("errorCode", TeamErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 팀 소속의 memberId를 지정하면 제외할 수 없다.")
    void test11() {
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