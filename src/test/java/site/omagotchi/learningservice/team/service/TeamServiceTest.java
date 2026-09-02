package site.omagotchi.learningservice.team.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.cohort.application.result.CohortMembershipView;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.team.application.TeamAccessSupport;
import site.omagotchi.learningservice.team.application.TeamDetailLookup;
import site.omagotchi.learningservice.team.application.TeamErrorCode;
import site.omagotchi.learningservice.team.application.TeamMembership;
import site.omagotchi.learningservice.team.application.TeamService;
import site.omagotchi.learningservice.team.application.port.IdentityAccountClient;
import site.omagotchi.learningservice.team.application.port.TeamMemberRepository;
import site.omagotchi.learningservice.team.application.port.TeamRepository;
import site.omagotchi.learningservice.team.application.result.TeamDetailLocalResult;
import site.omagotchi.learningservice.team.application.result.TeamDetailResult;
import site.omagotchi.learningservice.team.application.result.TeamMemberResult;
import site.omagotchi.learningservice.team.application.result.TeamResult;
import site.omagotchi.learningservice.team.domain.Team;
import site.omagotchi.learningservice.team.domain.TeamMember;
import site.omagotchi.learningservice.team.domain.TeamMemberRole;

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
    CohortMembershipQueryService cohortMembershipQueryService;

    @Mock
    IdentityAccountClient identityAccountClient;

    @Mock
    TeamDetailLookup teamDetailLookup;

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
    void creatingTeamRegistersCreatorAsMaster() {
        given(teamAccessSupport.resolveMembershipForCreate(1L, userId)).willReturn(membership);
        given(teamRepository.existsActiveByCohortIdAndName(1L, "오마고치")).willReturn(false);
        given(teamMemberRepository.existsByCohortMembershipId(10L)).willReturn(false);
        given(teamRepository.save(any())).willAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

        teamService.create(1L, "오마고치", userId);

        ArgumentCaptor<TeamMember> captor = ArgumentCaptor.forClass(TeamMember.class);
        verify(teamMemberRepository).save(captor.capture());
        assertThat(captor.getValue().isMaster()).isTrue();
        assertThat(captor.getValue().getCohortMembershipId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("이미 그 기수의 팀에 속해 있을 경우 팀을 생성할 수 없다.")
    void cannotCreateTeamWhenAlreadyInTeamForCohort() {
        given(teamAccessSupport.resolveMembershipForCreate(1L, userId)).willReturn(membership);
        given(teamRepository.existsActiveByCohortIdAndName(1L, "오마고치")).willReturn(false);
        given(teamMemberRepository.existsByCohortMembershipId(10L)).willReturn(true);

        assertThatThrownBy(() -> teamService.create(1L, "오마고치", userId))
                .hasFieldOrPropertyWithValue("errorCode", TeamErrorCode.ALREADY_IN_TEAM);
    }

    @Test
    @DisplayName("같은 기수에 이미 같은 이름의 활성 팀이 있으면 생성할 수 없다.")
    void cannotCreateTeamWithDuplicateActiveNameInCohort() {
        given(teamAccessSupport.resolveMembershipForCreate(1L, userId)).willReturn(membership);
        given(teamRepository.existsActiveByCohortIdAndName(1L, "오마고치")).willReturn(true);

        assertThatThrownBy(() -> teamService.create(1L, "오마고치", userId))
                .hasFieldOrPropertyWithValue("errorCode", TeamErrorCode.DUPLICATE_NAME);

        verify(teamRepository, never()).save(any());
        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("팀 상세 조회 시 마스터가 먼저 오고, 표시명이 채워진 팀원 목록을 반환한다. (GR-06, GR-15)")
    void teamDetailListsMasterFirstWithDisplayNames() {
        // Given: 마스터 우선으로 정렬된 Learning 조회 결과와 Identity 표시 이름
        Long teamId = 100L;
        UUID masterUserId = UUID.randomUUID();
        UUID memberUserId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        TeamDetailLocalResult localResult = new TeamDetailLocalResult(
                teamId,
                1L,
                "테스트",
                now.minusDays(1),
                1L,
                TeamMemberRole.MASTER,
                List.of(
                        new TeamDetailLocalResult.Member(
                                1L, masterUserId, TeamMemberRole.MASTER, now.plusMinutes(5)),
                        new TeamDetailLocalResult.Member(
                                2L, memberUserId, TeamMemberRole.MEMBER, now)
                )
        );

        given(teamDetailLookup.load(teamId, userId)).willReturn(localResult);
        given(identityAccountClient.findDisplayNames(any()))
                .willReturn(Map.of(masterUserId, "마스터닉네임", memberUserId, "멤버닉네임"));

        // When: 팀 상세 조회
        TeamDetailResult response = teamService.getTeam(teamId, userId);

        // Then: 조회 순서를 유지하면서 표시 이름을 붙인 팀원 목록 반환
        assertThat(response.myMemberId()).isEqualTo(1L);
        assertThat(response.myRole()).isEqualTo(TeamMemberRole.MASTER);
        assertThat(response.memberCount()).isEqualTo(2);
        assertThat(response.members())
                .extracting("displayName", "role")
                .containsExactly(
                        tuple("마스터닉네임", TeamMemberRole.MASTER),
                        tuple("멤버닉네임", TeamMemberRole.MEMBER)
                );
    }

    @Test
    @DisplayName("표시 이름이나 계정 참조가 없어도 팀원 행은 조회 결과에 남긴다. (GR-15)")
    void keepsMembersWhenIdentityDataIsMissing() {
        // Given: 한 계정 참조가 누락되고 Identity도 표시 이름을 돌려주지 않은 로컬 결과
        Long teamId = 100L;
        UUID knownUserId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        TeamDetailLocalResult localResult = new TeamDetailLocalResult(
                teamId,
                1L,
                "테스트",
                now.minusDays(1),
                1L,
                TeamMemberRole.MASTER,
                List.of(
                        new TeamDetailLocalResult.Member(
                                1L, knownUserId, TeamMemberRole.MASTER, now),
                        new TeamDetailLocalResult.Member(
                                2L, null, TeamMemberRole.MEMBER, now.plusMinutes(1))
                )
        );

        given(teamDetailLookup.load(teamId, userId)).willReturn(localResult);
        given(identityAccountClient.findDisplayNames(List.of(knownUserId))).willReturn(Map.of());

        // When: 팀 상세 조회
        TeamDetailResult result = teamService.getTeam(teamId, userId);

        // Then: null 계정 참조는 Identity에 보내지 않고 두 팀원 행을 모두 유지
        verify(identityAccountClient).findDisplayNames(List.of(knownUserId));
        assertThat(result.members())
                .extracting(TeamMemberResult::memberId, TeamMemberResult::displayName)
                .containsExactly(tuple(1L, null), tuple(2L, null));
        assertThat(result.memberCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("팀 소속이 아니면 팀 상세를 조회할 수 없다.")
    void cannotViewTeamDetailWithoutMembership() {
        // Given: Learning 조회 단계에서 팀 접근 권한이 거부되는 요청자
        Long teamId = 100L;
        given(teamDetailLookup.load(teamId, userId))
                .willThrow(new BusinessException(TeamErrorCode.NOT_A_MEMBER));

        // When & Then: 팀 소속 오류 반환
        assertThatThrownBy(() -> teamService.getTeam(teamId, userId))
                .hasFieldOrPropertyWithValue("errorCode", TeamErrorCode.NOT_A_MEMBER);

        // Then: 권한 실패 뒤 Identity 조회 시도 없음
        verify(identityAccountClient, never()).findDisplayNames(any());
    }

    @Test
    @DisplayName("소속된 팀 목록을 반환한다. (GR-06)")
    void returnsListOfMyTeams() {
        CohortMembershipView m1 = new CohortMembershipView(10L, 1L, userId);
        CohortMembershipView m2 = new CohortMembershipView(20L, 2L, userId);
        given(cohortMembershipQueryService.findActiveMemberships(userId)).willReturn(List.of(m1, m2));

        TeamMember tm1 = createMember(1L, 100L, 10L, TeamMemberRole.MASTER, OffsetDateTime.now());
        TeamMember tm2 = createMember(2L, 200L, 20L, TeamMemberRole.MASTER, OffsetDateTime.now());
        given(teamMemberRepository.findByCohortMembershipIdIn(List.of(10L, 20L)))
                .willReturn(List.of(tm1, tm2));

        Team team1 = createTeamWithId(100L, 1L);
        Team team2 = createTeamWithId(200L, 2L);
        given(teamRepository.findByIdInAndDeletedAtIsNull(List.of(100L, 200L)))
                .willReturn(List.of(team1, team2));

        List<TeamResult> result = teamService.getMyTeams(userId);

        assertThat(result).extracting(TeamResult::teamId).containsExactly(100L, 200L);
    }

    @Test
    @DisplayName("활성 멤버십이 없으면 빈 목록을 반환한다.")
    void returnsEmptyListWhenNoActiveMembership() {
        given(cohortMembershipQueryService.findActiveMemberships(userId)).willReturn(List.of());

        List<TeamResult> result = teamService.getMyTeams(userId);

        assertThat(result).isEmpty();
        verify(teamMemberRepository, never()).findByCohortMembershipIdIn(any());
        verify(teamRepository, never()).findByIdInAndDeletedAtIsNull(any());
    }

    @Test
    @DisplayName("멤버십은 있지만 소속된 팀이 없으면 빈 목록을 반환한다.")
    void returnsEmptyListWhenMembershipHasNoTeam() {
        CohortMembershipView m1 = new CohortMembershipView(10L, 1L, userId);
        given(cohortMembershipQueryService.findActiveMemberships(userId)).willReturn(List.of(m1));
        given(teamMemberRepository.findByCohortMembershipIdIn(List.of(10L))).willReturn(List.of());

        List<TeamResult> result = teamService.getMyTeams(userId);

        assertThat(result).isEmpty();
        verify(teamRepository, never()).findByIdInAndDeletedAtIsNull(any());
    }
}
