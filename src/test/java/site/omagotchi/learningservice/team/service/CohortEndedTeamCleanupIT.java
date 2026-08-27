package site.omagotchi.learningservice.team.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.team.application.CohortEndedTeamCleanup;
import site.omagotchi.learningservice.team.application.TeamMasterService;
import site.omagotchi.learningservice.team.application.TeamService;
import site.omagotchi.learningservice.team.support.TeamTestFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;

/**
 * 기수 종료 팀 정리 (CE-01, 명세 08 §2 1단계).
 *
 * <p><b>팀별 Transaction 격리가 이 Class의 존재 이유라 실제 DB가 필요하다.</b> 한 팀의
 * 실패가 다른 팀을 롤백시키는지는 Mock으로 관찰할 수 없다 — 커밋 여부를 직접 봐야 한다.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TeamTestFixture.class})
class CohortEndedTeamCleanupIT {

    @Autowired
    TeamTestFixture fixture;

    @Autowired
    TeamService teamService;

    @Autowired
    CohortEndedTeamCleanup teamCleanup;

    @Autowired
    JdbcTemplate jdbcTemplate;

    /** 팀 하나만 골라 실패시키기 위한 Spy. */
    @MockitoSpyBean
    TeamMasterService spiedTeamMasterService;

    @Test
    @DisplayName("기수의 활성 팀을 전부 해체하고 팀원 행을 물리 삭제한다.")
    void disbandsEveryActiveTeamOfCohort() {
        Long cohortId = fixture.createCohort("팀정리-전체");
        TeamTestFixture.Member masterA = fixture.createActiveMember(cohortId);
        TeamTestFixture.Member masterB = fixture.createActiveMember(cohortId);
        Long teamA = teamService.create(cohortId, "팀정리-전체-A", masterA.userId()).teamId();
        Long teamB = teamService.create(cohortId, "팀정리-전체-B", masterB.userId()).teamId();

        assertThat(teamCleanup.disbandAllByCohort(cohortId)).isEqualTo(2);

        assertThat(isDisbanded(teamA)).isTrue();
        assertThat(isDisbanded(teamB)).isTrue();
        assertThat(memberRows(teamA)).isZero();
        assertThat(memberRows(teamB)).isZero();
    }

    /**
     * <b>이 테스트가 리뷰로 찾은 회귀를 고정한다.</b> 예전에는 팀 순회가 단일 Transaction
     * 안에 있어, 한 팀의 실패가 앞서 해체된 팀까지 롤백시켰다. 지금은 팀마다
     * {@link TeamMasterService#disbandOne}이 자기 Transaction을 가져 격리된다.
     */
    @Test
    @DisplayName("한 팀의 해체가 실패해도 다른 팀은 실제로 커밋된다.")
    void oneTeamFailureDoesNotRollbackOthers() {
        Long cohortId = fixture.createCohort("팀정리-격리");
        TeamTestFixture.Member masterA = fixture.createActiveMember(cohortId);
        TeamTestFixture.Member masterB = fixture.createActiveMember(cohortId);
        Long failingTeam = teamService.create(cohortId, "팀정리-격리-실패", masterA.userId()).teamId();
        Long survivingTeam = teamService.create(cohortId, "팀정리-격리-생존", masterB.userId()).teamId();

        willThrow(new IllegalStateException("의도된 실패"))
                .given(spiedTeamMasterService).disbandOne(failingTeam);

        assertThatCode(() -> teamCleanup.disbandAllByCohort(cohortId)).doesNotThrowAnyException();

        // 실패한 팀은 그대로 남는다 — 멤버십 정합성 스윕이 뒤늦게 받칠 몫이다.
        assertThat(isDisbanded(failingTeam)).isFalse();
        // 실패와 무관한 팀은 실제로 커밋됐다.
        assertThat(isDisbanded(survivingTeam)).isTrue();
    }

    /** 같은 기수를 두 번 정리해도 안전하다 — 두 번째는 활성 팀 조회가 빈 결과다. */
    @Test
    @DisplayName("같은 기수를 두 번 정리하면 두 번째는 해체할 팀이 없다.")
    void isIdempotentAcrossRuns() {
        Long cohortId = fixture.createCohort("팀정리-멱등");
        TeamTestFixture.Member master = fixture.createActiveMember(cohortId);
        teamService.create(cohortId, "팀정리-멱등-A", master.userId());

        assertThat(teamCleanup.disbandAllByCohort(cohortId)).isEqualTo(1);
        assertThat(teamCleanup.disbandAllByCohort(cohortId)).isZero();
    }

    private boolean isDisbanded(Long teamId) {
        Boolean disbanded = jdbcTemplate.queryForObject("""
                SELECT deleted_at IS NOT NULL FROM learning_service.teams WHERE id = ?
                """, Boolean.class, teamId);
        return Boolean.TRUE.equals(disbanded);
    }

    private int memberRows(Long teamId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM learning_service.team_members WHERE team_id = ?
                """, Integer.class, teamId);
        return count == null ? 0 : count;
    }
}
