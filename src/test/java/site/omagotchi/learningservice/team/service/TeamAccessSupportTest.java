package site.omagotchi.learningservice.team.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.cohort.application.result.CohortMembershipView;
import site.omagotchi.learningservice.team.application.TeamAccessSupport;
import site.omagotchi.learningservice.team.application.TeamMembership;
import site.omagotchi.learningservice.team.domain.Team;
import site.omagotchi.learningservice.team.application.TeamErrorCode;
import site.omagotchi.learningservice.team.domain.TeamMember;
import site.omagotchi.learningservice.team.application.port.TeamMemberRepository;
import site.omagotchi.learningservice.team.application.port.TeamRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class TeamAccessSupportTest {

    @Mock
    TeamRepository teamRepository;

    @Mock
    TeamMemberRepository teamMemberRepository;

    @Mock
    CohortMembershipQueryService cohortMembershipQueryService;

    @InjectMocks
    TeamAccessSupport teamAccessSupport;

    private final UUID userId = UUID.randomUUID();

    @Test
    @DisplayName("활성 기수가 하나면 지정하지 않아도 그 기수로 결정")
    void test1() {
        given(cohortMembershipQueryService.findActiveMemberships(userId))
                .willReturn(List.of(new CohortMembershipView(10L, 1L, userId)));

        assertThat(teamAccessSupport.resolveMembershipForCreate(null, userId).cohortId())
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("활성 기수가 둘 이상이면 대상 기수 지정을 요구")
    void test2() {
        given(cohortMembershipQueryService.findActiveMemberships(userId)).willReturn(List.of(
                new CohortMembershipView(10L, 1L, userId),
                new CohortMembershipView(11L, 2L, userId)
        ));

        assertThatThrownBy(() -> teamAccessSupport.resolveMembershipForCreate(null, userId))
                .hasFieldOrPropertyWithValue("errorCode", TeamErrorCode.COHORT_REQUIRED);
    }

    @Test
    @DisplayName("담당하지 않는 기수를 지정할 경우 거부")
    void test3() {
        given(cohortMembershipQueryService.findActiveMembership(99L, userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamAccessSupport.resolveMembershipForCreate(99L, userId))
                .hasFieldOrPropertyWithValue("errorCode", TeamErrorCode.COHORT_ACCESS_DENIED);
    }

    @Test
    @DisplayName("활성 멤버십이 있으면 그대로 반환한다.")
    void test4() {
        given(cohortMembershipQueryService.findActiveMembership(1L, userId))
                .willReturn(Optional.of(new CohortMembershipView(10L, 1L, userId)));

        // 기수 파트의 View가 팀의 TeamMembership으로 변환돼 나오는지까지 고정한다.
        assertThat(teamAccessSupport.requireActiveMembership(1L, userId))
                .isEqualTo(new TeamMembership(10L, 1L, userId));
    }

    @Test
    @DisplayName("활성 멤버십이 없으면 거부한다.")
    void test5() {
        given(cohortMembershipQueryService.findActiveMembership(1L, userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamAccessSupport.requireActiveMembership(1L, userId))
                .hasFieldOrPropertyWithValue("errorCode", TeamErrorCode.COHORT_ACCESS_DENIED);
    }

    @Test
    @DisplayName("해체되지 않은 팀은 조회된다.")
    void test6() {
        Team team = Team.create(1L, "테스트");
        given(teamRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.of(team));

        assertThat(teamAccessSupport.loadActiveTeam(1L)).isEqualTo(team);
    }

    @Test
    @DisplayName("존재하지 않는 팀은 조회할 수 없다.")
    void test7() {
        given(teamRepository.findByIdAndDeletedAtIsNull(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamAccessSupport.loadActiveTeam(1L))
                .hasFieldOrPropertyWithValue("errorCode", TeamErrorCode.TEAM_NOT_FOUND);
    }

    @Test
    @DisplayName("락 대상 팀이 해체 전이면 그대로 반환한다.")
    void test8() {
        Team team = Team.create(1L, "테스트");
        given(teamRepository.findByIdForUpdate(1L)).willReturn(Optional.of(team));

        assertThat(teamAccessSupport.lockActiveTeam(1L)).isEqualTo(team);
    }

    @Test
    @DisplayName("락 대상 팀 행이 없으면 거부한다.")
    void test9() {
        given(teamRepository.findByIdForUpdate(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> teamAccessSupport.lockActiveTeam(1L))
                .hasFieldOrPropertyWithValue("errorCode", TeamErrorCode.TEAM_NOT_FOUND);
    }

    @Test
    @DisplayName("락을 잡았더니 이미 해체된 팀이면 거부한다.")
    void test10() {
        Team team = Team.create(1L, "테스트");
        team.disband();
        given(teamRepository.findByIdForUpdate(1L)).willReturn(Optional.of(team));

        assertThatThrownBy(() -> teamAccessSupport.lockActiveTeam(1L))
                .hasFieldOrPropertyWithValue("errorCode", TeamErrorCode.TEAM_NOT_FOUND);
    }

    @Test
    @DisplayName("팀 소속이면 팀원 행을 반환한다.")
    void test11() {
        TeamMember member = TeamMember.member(1L, 10L);
        given(teamMemberRepository.findByTeamIdAndCohortMembershipId(1L, 10L))
                .willReturn(Optional.of(member));

        assertThat(teamAccessSupport.requireMembership(1L, 10L)).isEqualTo(member);
    }

    @Test
    @DisplayName("팀 소속이 아니면 거부한다.")
    void test12() {
        given(teamMemberRepository.findByTeamIdAndCohortMembershipId(1L, 10L))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> teamAccessSupport.requireMembership(1L, 10L))
                .hasFieldOrPropertyWithValue("errorCode", TeamErrorCode.NOT_A_MEMBER);
    }

    @Test
    @DisplayName("마스터면 그대로 반환한다.")
    void test13() {
        TeamMember master = TeamMember.master(1L, 10L);
        given(teamMemberRepository.findByTeamIdAndCohortMembershipId(1L, 10L))
                .willReturn(Optional.of(master));

        assertThat(teamAccessSupport.requireMaster(1L, 10L)).isEqualTo(master);
    }

    @Test
    @DisplayName("마스터가 아니면 거부한다.")
    void test14() {
        TeamMember member = TeamMember.member(1L, 10L);
        given(teamMemberRepository.findByTeamIdAndCohortMembershipId(1L, 10L))
                .willReturn(Optional.of(member));

        assertThatThrownBy(() -> teamAccessSupport.requireMaster(1L, 10L))
                .hasFieldOrPropertyWithValue("errorCode", TeamErrorCode.MASTER_REQUIRED);
    }
}
