package site.omagotchi.learningservice.team.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.team.application.TeamMemberAddition;
import site.omagotchi.learningservice.team.application.TeamMemberService;
import site.omagotchi.learningservice.team.application.TeamService;
import site.omagotchi.learningservice.team.application.port.TeamMemberRepository;
import site.omagotchi.learningservice.team.support.TeamTestFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.willAnswer;

/** 팀원 추가의 별도 쓰기 트랜잭션이 실패를 실제 DB 롤백으로 끝내는지 확인한다. */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TeamTestFixture.class})
class TeamMemberAddTransactionIT {

    @Autowired
    TeamTestFixture fixture;

    @Autowired
    TeamService teamService;

    @Autowired
    TeamMemberService teamMemberService;

    @Autowired
    TeamMemberRepository teamMemberRepository;

    @MockitoSpyBean
    TeamMemberAddition teamMemberAddition;

    @Test
    @DisplayName("팀원 INSERT 뒤 쓰기 작업이 실패하면 추가 행이 롤백된다")
    void rollsBackInsertWhenAdditionFailsAfterSave() {
        // Given: 추가 가능한 팀과 저장 직후 강제로 실패하는 쓰기 작업
        Long cohortId = fixture.createCohort("팀원 추가 롤백");
        TeamTestFixture.Member master = fixture.createActiveMember(cohortId);
        TeamTestFixture.Member target = fixture.createActiveMember(cohortId);
        Long teamId = teamService.create(cohortId, "롤백 팀", master.userId()).teamId();

        willAnswer(invocation -> {
            invocation.callRealMethod();
            throw new ForcedFailure();
        }).given(teamMemberAddition).add(teamId, target.userId(), master.userId());

        // When & Then: 저장 이후의 실패가 호출자에게 전파
        assertThatThrownBy(() ->
                teamMemberService.addMember(teamId, target.userId(), master.userId()))
                .isInstanceOf(ForcedFailure.class);

        // Then: 팀원 추가 트랜잭션의 INSERT 롤백
        assertThat(teamMemberRepository
                .findByTeamIdAndCohortMembershipId(teamId, target.membershipId()))
                .isEmpty();
    }

    private static final class ForcedFailure extends RuntimeException {
    }
}
