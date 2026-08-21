package site.omagotchi.learningservice.team.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import site.omagotchi.learningservice.team.application.port.TeamMemberRepository;
import site.omagotchi.learningservice.team.application.port.TeamRepository;
import site.omagotchi.learningservice.team.application.result.CurrentTeamMembershipView;
import site.omagotchi.learningservice.team.domain.Team;
import site.omagotchi.learningservice.team.domain.TeamMember;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("현재 팀 소속 조회")
@ExtendWith(MockitoExtension.class)
class CurrentTeamMembershipQueryServiceTest {

    private static final Long COHORT_ID = 10L;
    private static final Long TEAM_ID = 100L;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @InjectMocks
    private CurrentTeamMembershipQueryService queryService;

    @Test
    @DisplayName("다른 기수와 해체 팀을 제외한 현재 팀 소속 일괄 반환")
    void returnsCurrentMembershipsInRequestedCohort() {
        Team firstTeam = team(TEAM_ID, COHORT_ID, "첫 팀");
        Team secondTeam = team(200L, COHORT_ID, "둘째 팀");
        Team otherCohortTeam = team(300L, 20L, "다른 기수 팀");
        given(teamMemberRepository.findByCohortMembershipIdIn(List.of(11L, 12L, 13L, 14L)))
                .willReturn(List.of(
                        TeamMember.member(TEAM_ID, 11L),
                        TeamMember.member(200L, 12L),
                        TeamMember.member(300L, 13L),
                        TeamMember.member(400L, 14L)
                ));
        given(teamRepository.findByIdInAndDeletedAtIsNull(List.of(TEAM_ID, 200L, 300L, 400L)))
                .willReturn(List.of(firstTeam, secondTeam, otherCohortTeam));

        List<CurrentTeamMembershipView> result = queryService.findCurrentMemberships(
                COHORT_ID,
                List.of(11L, 12L, 13L, 14L)
        );

        assertEquals(
                List.of(
                        new CurrentTeamMembershipView(TEAM_ID, "첫 팀", 11L),
                        new CurrentTeamMembershipView(200L, "둘째 팀", 12L)
                ),
                result
        );
        verify(teamMemberRepository)
                .findByCohortMembershipIdIn(List.of(11L, 12L, 13L, 14L));
        verify(teamRepository)
                .findByIdInAndDeletedAtIsNull(List.of(TEAM_ID, 200L, 300L, 400L));
    }

    @Test
    @DisplayName("후보가 없으면 저장소를 조회하지 않고 빈 결과 반환")
    void returnsEmptyWithoutRepositoryQueries() {
        List<CurrentTeamMembershipView> result = queryService.findCurrentMemberships(
                COHORT_ID,
                List.of()
        );

        assertEquals(List.of(), result);
        verifyNoInteractions(teamRepository, teamMemberRepository);
    }

    private Team team(Long teamId, Long cohortId, String name) {
        Team team = Team.create(cohortId, name);
        ReflectionTestUtils.setField(team, "id", teamId);
        return team;
    }
}
