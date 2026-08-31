package site.omagotchi.learningservice.team.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.team.application.TeamErrorCode;
import site.omagotchi.learningservice.team.application.TeamMasterService;
import site.omagotchi.learningservice.team.application.TeamMemberService;
import site.omagotchi.learningservice.team.application.TeamService;
import site.omagotchi.learningservice.team.application.port.IdentityAccountClient;
import site.omagotchi.learningservice.team.application.port.IdentityAccountState;
import site.omagotchi.learningservice.team.application.port.TeamMemberRepository;
import site.omagotchi.learningservice.team.support.TeamTestFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.willAnswer;

/** 팀원 추가의 Identity 조회 중 팀이 해체되는 레이스 회귀 테스트. */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TeamTestFixture.class})
class TeamDisbandRaceIT {

    @Autowired
    TeamTestFixture fixture;

    @Autowired
    TeamService teamService;

    @Autowired
    TeamMemberService teamMemberService;

    @Autowired
    TeamMasterService teamMasterService;

    @Autowired
    TeamMemberRepository teamMemberRepository;

    @MockitoSpyBean
    IdentityAccountClient identityAccountClient;

    /**
     * 사전 권한 검사가 통과한 뒤 Identity 응답을 기다리는 사이 팀 해체를 커밋한다.
     * 뒤따르는 쓰기 작업은 새 트랜잭션에서 팀 행을 잠근 뒤 deleted_at을 다시 확인하므로
     * 404로 중단하고 대상 팀원 행을 남기지 않아야 한다.
     */
    @Test
    @DisplayName("Identity 조회 중 팀이 해체되면 팀원 추가가 404로 거부된다")
    void addMemberRejectsTeamDisbandedDuringIdentityLookup() {
        // Given: 추가 가능한 팀과 Identity 조회 중 팀을 해체하는 응답
        Long cohortId = fixture.createCohort("해체 레이스 기수");
        TeamTestFixture.Member master = fixture.createActiveMember(cohortId);
        TeamTestFixture.Member target = fixture.createActiveMember(cohortId);
        Long teamId = teamService.create(cohortId, "해체 레이스 팀", master.userId()).teamId();

        // getState는 사전 권한 검사 이후, 쓰기 작업이 팀 락을 잡기 직전의 결정적 주입 지점이다.
        // addMember 바깥에는 트랜잭션이 없으므로 같은 스레드의 disband가 독립 트랜잭션으로 커밋된다.
        willAnswer(invocation -> {
            teamMasterService.disband(teamId, master.userId());
            return IdentityAccountState.ACTIVE;
        }).given(identityAccountClient).getState(target.userId());

        // When & Then: 해체 이후 시작한 쓰기 작업이 요청을 거부
        assertThatThrownBy(() ->
                teamMemberService.addMember(teamId, target.userId(), master.userId()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(TeamErrorCode.TEAM_NOT_FOUND);

        // Then: 해체된 팀에 대상 팀원 행이 남지 않음
        assertThat(teamMemberRepository
                .findByTeamIdAndCohortMembershipId(teamId, target.membershipId()))
                .isEmpty();
        assertThat(teamMemberRepository.countByTeamId(teamId)).isZero();
    }
}
