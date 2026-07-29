package site.omagotchi.learningservice.team.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.team.application.*;
import site.omagotchi.learningservice.team.application.dto.command.CreateTeamRequest;
import site.omagotchi.learningservice.team.application.dto.result.TeamDetailResponse;
import site.omagotchi.learningservice.team.application.dto.result.TeamResponse;
import site.omagotchi.learningservice.team.domain.Team;
import site.omagotchi.learningservice.team.domain.TeamErrorCode;
import site.omagotchi.learningservice.team.domain.TeamMember;
import site.omagotchi.learningservice.team.domain.TeamMemberRole;
import site.omagotchi.learningservice.team.infrastructure.TeamMemberRepository;
import site.omagotchi.learningservice.team.infrastructure.TeamRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.*;



@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @Mock
    TeamRepository teamRepository;

    @Mock
    TeamMemberRepository teamMemberRepository;

    @Mock
    TeamAccessSupport teamAccessSupport;

    @Mock
    MembershipReader membershipReader;

    @Mock
    AccountReader accountReader;

    @InjectMocks
    TeamService teamService;

    private final UUID userId = UUID.randomUUID();
    private final TeamMembership membership = new TeamMembership(10L, 1L, userId);

    private static Team createTeamWithId(Long id, Long cohortId) {
        Team team = Team.create(cohortId, "테스트");
        ReflectionTestUtils.setField(team, "id", id);
        return team;
    }

    private static TeamMember createMember(
            Long id, Long teamId, Long cohortMembershipId, TeamMemberRole role, OffsetDateTime joinedAt
    ) {
        TeamMember member = role == TeamMemberRole.MASTER
                ? TeamMember.master(teamId, cohortMembershipId)
                : TeamMember.member(teamId, cohortMembershipId);
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "joinedAt", joinedAt);
        return member;
    }

    @Test
    @DisplayName("팀을 생성하면 생성자가 마스터로 등록된다.")
    void test1() {
        given(teamAccessSupport.resolveMembershipForCreate(1L, userId)).willReturn(membership);
        given(teamRepository.existsActiveByCohortIdAndName(1L, "오마고치")).willReturn(false);
        given(teamMemberRepository.existsByCohortMembershipId(10L)).willReturn(false);
        given(teamRepository.saveAndFlush(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        teamService.create(new CreateTeamRequest(1L, "오마고치"), userId);

        ArgumentCaptor<TeamMember> captor = ArgumentCaptor.forClass(TeamMember.class);
        verify(teamMemberRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().isMaster()).isTrue();
        assertThat(captor.getValue().getCohortMembershipId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("이미 그 기수의 팀에 속해 있을 경우 팀을 생성할 수 없다.")
    void test2() {
        given(teamAccessSupport.resolveMembershipForCreate(1L, userId)).willReturn(membership);
        given(teamRepository.existsActiveByCohortIdAndName(1L, "오마고치")).willReturn(false);
        given(teamMemberRepository.existsByCohortMembershipId(10L)).willReturn(true);

        assertThatThrownBy(() -> teamService.create(new CreateTeamRequest(1L, "오마고치"), userId))
                .hasFieldOrPropertyWithValue("errorCode", TeamErrorCode.ALREADY_IN_TEAM);
    }

    @Test
    @DisplayName("같은 기수에 이미 같은 이름의 활성 팀이 있으면 생성할 수 없다.")
    void test3() {
        given(teamAccessSupport.resolveMembershipForCreate(1L, userId)).willReturn(membership);
        given(teamRepository.existsActiveByCohortIdAndName(1L, "오마고치")).willReturn(true);

        assertThatThrownBy(() -> teamService.create(new CreateTeamRequest(1L, "오마고치"), userId))
                .hasFieldOrPropertyWithValue("errorCode", TeamErrorCode.DUPLICATE_NAME);

        verify(teamRepository, never()).saveAndFlush(any());
        verify(teamMemberRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("팀 상세 조회 시 마스터가 먼저 오고, 표시명이 채워진 팀원 목록을 반환한다. (GR-06, GR-15)")
    void test4() {
        Long teamId = 100L;
        Team team = createTeamWithId(teamId, 1L);

        UUID masterUserId = UUID.randomUUID();
        UUID memberUserId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        // 리포지토리가 joinedAt 오름차순으로 반환해도, 서비스는 마스터를 항상 앞에 두어야 한다.
        TeamMember masterMember = createMember(1L, teamId, 10L, TeamMemberRole.MASTER, now.plusMinutes(5));
        TeamMember normalMember = createMember(2L, teamId, 20L, TeamMemberRole.MEMBER, now);

        given(teamAccessSupport.loadActiveTeam(teamId)).willReturn(team);
        given(teamAccessSupport.requiredActiveMembership(1L, userId)).willReturn(membership);
        given(teamAccessSupport.requireMembership(teamId, 10L)).willReturn(masterMember);
        given(teamMemberRepository.findByTeamIdOrderByJoinedAtAsc(teamId))
                .willReturn(List.of(normalMember, masterMember));
        given(membershipReader.findUserIds(any()))
                .willReturn(Map.of(10L, masterUserId, 20L, memberUserId));
        given(accountReader.findDisplayNames(any()))
                .willReturn(Map.of(masterUserId, "마스터닉네임", memberUserId, "멤버닉네임"));

        TeamDetailResponse response = teamService.getTeam(teamId, userId);

        assertThat(response.memberCount()).isEqualTo(2);
        assertThat(response.members())
                .extracting("displayName", "role")
                .containsExactly(
                        tuple("마스터닉네임", TeamMemberRole.MASTER),
                        tuple("멤버닉네임", TeamMemberRole.MEMBER)
                );
    }

    @Test
    @DisplayName("팀 소속이 아니면 팀 상세를 조회할 수 없다.")
    void test5() {
        Long teamId = 100L;
        Team team = createTeamWithId(teamId, 1L);

        given(teamAccessSupport.loadActiveTeam(teamId)).willReturn(team);
        given(teamAccessSupport.requiredActiveMembership(1L, userId))
                .willThrow(new BusinessException(TeamErrorCode.NOT_A_MEMBER));

        assertThatThrownBy(() -> teamService.getTeam(teamId, userId))
                .hasFieldOrPropertyWithValue("errorCode", TeamErrorCode.NOT_A_MEMBER);

        verify(teamMemberRepository, never()).findByTeamIdOrderByJoinedAtAsc(any());
    }

    @Test
    @DisplayName("소속된 팀 목록을 반환한다. (GR-06)")
    void test6() {
        TeamMembership m1 = new TeamMembership(10L, 1L, userId);
        TeamMembership m2 = new TeamMembership(20L, 2L, userId);
        given(membershipReader.findActiveAll(userId)).willReturn(List.of(m1, m2));

        TeamMember tm1 = createMember(1L, 100L, 10L, TeamMemberRole.MASTER, OffsetDateTime.now());
        TeamMember tm2 = createMember(2L, 200L, 20L, TeamMemberRole.MASTER, OffsetDateTime.now());
        given(teamMemberRepository.findByCohortMembershipIdIn(List.of(10L, 20L)))
                .willReturn(List.of(tm1, tm2));

        Team team1 = createTeamWithId(100L, 1L);
        Team team2 = createTeamWithId(200L, 2L);
        given(teamRepository.findByIdInAndDeletedAtIsNull(List.of(100L, 200L)))
                .willReturn(List.of(team1, team2));

        List<TeamResponse> result = teamService.getMyTeams(userId);

        assertThat(result).extracting(TeamResponse::teamId).containsExactly(100L, 200L);
    }

    @Test
    @DisplayName("활성 멤버십이 없으면 빈 목록을 반환한다.")
    void test7() {
        given(membershipReader.findActiveAll(userId)).willReturn(List.of());

        List<TeamResponse> result = teamService.getMyTeams(userId);

        assertThat(result).isEmpty();
        verify(teamMemberRepository, never()).findByCohortMembershipIdIn(any());
        verify(teamRepository, never()).findByIdInAndDeletedAtIsNull(any());
    }

    @Test
    @DisplayName("멤버십은 있지만 소속된 팀이 없으면 빈 목록을 반환한다.")
    void test8() {
        TeamMembership m1 = new TeamMembership(10L, 1L, userId);
        given(membershipReader.findActiveAll(userId)).willReturn(List.of(m1));
        given(teamMemberRepository.findByCohortMembershipIdIn(List.of(10L))).willReturn(List.of());

        List<TeamResponse> result = teamService.getMyTeams(userId);

        assertThat(result).isEmpty();
        verify(teamRepository, never()).findByIdInAndDeletedAtIsNull(any());
    }
}
