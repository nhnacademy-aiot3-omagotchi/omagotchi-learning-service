package site.omagotchi.learningservice.team.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.cohort.application.result.CohortMembershipView;
import site.omagotchi.learningservice.team.application.TeamAccessSupport;
import site.omagotchi.learningservice.team.application.TeamErrorCode;
import site.omagotchi.learningservice.team.application.TeamMemberAddition;
import site.omagotchi.learningservice.team.application.TeamMembership;
import site.omagotchi.learningservice.team.application.port.TeamMemberRepository;
import site.omagotchi.learningservice.team.domain.Team;
import site.omagotchi.learningservice.team.domain.TeamMember;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TeamMemberAdditionTest {

    @Mock
    TeamMemberRepository teamMemberRepository;

    @Mock
    TeamAccessSupport accessSupport;

    @Mock
    CohortMembershipQueryService cohortMembershipQueryService;

    @InjectMocks
    TeamMemberAddition teamMemberAddition;

    private final UUID requesterUserId = UUID.randomUUID();
    private final UUID targetUserId = UUID.randomUUID();

    @Test
    @DisplayName("락 이후 현재 권한·대상 소속·정원을 확인하고 팀원을 추가한다")
    void addsMemberFromLockedCurrentState() {
        // Given: 현재 MASTER와 추가 가능한 대상, 여유가 있는 팀
        Team team = teamWithId(1L, 10L);
        TeamMembership requesterMembership = new TeamMembership(100L, 10L, requesterUserId);
        CohortMembershipView targetMembership =
                new CohortMembershipView(200L, 10L, targetUserId);

        given(accessSupport.lockActiveTeam(1L)).willReturn(team);
        given(accessSupport.requireActiveMembership(10L, requesterUserId))
                .willReturn(requesterMembership);
        given(cohortMembershipQueryService.findActiveMembership(10L, targetUserId))
                .willReturn(Optional.of(targetMembership));
        given(teamMemberRepository.countByTeamId(1L)).willReturn(7L);

        // When: 팀원 추가 쓰기 트랜잭션 실행
        teamMemberAddition.add(1L, targetUserId, requesterUserId);

        // Then: 팀 락 이후 현재 상태를 순서대로 확인하고 일반 팀원을 저장
        ArgumentCaptor<TeamMember> memberCaptor = ArgumentCaptor.forClass(TeamMember.class);
        InOrder order = inOrder(
                accessSupport,
                cohortMembershipQueryService,
                teamMemberRepository
        );
        order.verify(accessSupport).lockActiveTeam(1L);
        order.verify(accessSupport).requireActiveMembership(10L, requesterUserId);
        order.verify(accessSupport).requireMaster(1L, 100L);
        order.verify(cohortMembershipQueryService).findActiveMembership(10L, targetUserId);
        order.verify(teamMemberRepository).countByTeamId(1L);
        order.verify(teamMemberRepository).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getTeamId()).isEqualTo(1L);
        assertThat(memberCaptor.getValue().getCohortMembershipId()).isEqualTo(200L);
        assertThat(memberCaptor.getValue().isMaster()).isFalse();
    }

    @Test
    @DisplayName("Identity 조회 뒤 대상 소속이 끝났으면 팀원 추가를 거부한다")
    void rejectsMemberWhoseMembershipEndedBeforeAddition() {
        // Given: 요청자는 현재 MASTER지만 대상의 활성 소속은 종료된 상태
        Team team = teamWithId(1L, 10L);
        TeamMembership requesterMembership = new TeamMembership(100L, 10L, requesterUserId);

        given(accessSupport.lockActiveTeam(1L)).willReturn(team);
        given(accessSupport.requireActiveMembership(10L, requesterUserId))
                .willReturn(requesterMembership);
        given(cohortMembershipQueryService.findActiveMembership(10L, targetUserId))
                .willReturn(Optional.empty());

        // When & Then: 현재 소속을 다시 확인해 추가를 거부
        assertThatThrownBy(() -> teamMemberAddition.add(1L, targetUserId, requesterUserId))
                .hasFieldOrPropertyWithValue("errorCode", TeamErrorCode.TARGET_NOT_IN_COHORT);

        // Then: 팀원 저장 시도 없음
        verify(teamMemberRepository, never()).save(any());
    }

    @Test
    @DisplayName("락 이후 정원이 가득 찼으면 팀원 추가를 거부한다")
    void rejectsMemberWhenLockedTeamIsFull() {
        // Given: 현재 MASTER와 활성 대상이 있지만 정원이 가득 찬 팀
        Team team = teamWithId(1L, 10L);
        TeamMembership requesterMembership = new TeamMembership(100L, 10L, requesterUserId);
        CohortMembershipView targetMembership =
                new CohortMembershipView(200L, 10L, targetUserId);

        given(accessSupport.lockActiveTeam(1L)).willReturn(team);
        given(accessSupport.requireActiveMembership(10L, requesterUserId))
                .willReturn(requesterMembership);
        given(cohortMembershipQueryService.findActiveMembership(10L, targetUserId))
                .willReturn(Optional.of(targetMembership));
        given(teamMemberRepository.countByTeamId(1L)).willReturn(8L);

        // When & Then: 락 안에서 확인한 정원을 기준으로 추가를 거부
        assertThatThrownBy(() -> teamMemberAddition.add(1L, targetUserId, requesterUserId))
                .hasFieldOrPropertyWithValue("errorCode", TeamErrorCode.CAPACITY_EXCEEDED);

        // Then: 팀원 저장 시도 없음
        verify(teamMemberRepository, never()).save(any());
    }

    private static Team teamWithId(Long teamId, Long cohortId) {
        Team team = Team.create(cohortId, "테스트");
        ReflectionTestUtils.setField(team, "id", teamId);
        return team;
    }
}
