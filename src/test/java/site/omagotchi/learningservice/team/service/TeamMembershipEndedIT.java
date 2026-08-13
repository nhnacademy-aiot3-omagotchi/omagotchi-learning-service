package site.omagotchi.learningservice.team.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.cohort.application.CohortMembershipService;
import site.omagotchi.learningservice.team.application.TeamMasterService;
import site.omagotchi.learningservice.team.application.TeamMemberService;
import site.omagotchi.learningservice.team.application.TeamService;
import site.omagotchi.learningservice.team.support.TeamTestFixture;

import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 소속 종료에 따른 팀 정리 (GR-16).
 *
 * <p>실제 PostgreSQL이 있어야 의미가 있다 — 이 흐름이 지켜야 할 것 대부분이 DB 제약이다.
 * {@code uq_team_members_one_master}는 "최대 1명"만 보장해 MASTER 0명을 막지 못하고,
 * {@code uq_team_members_membership}은 상태 조건이 없어 남은 행이 재가입을 영구히 막는다.</p>
 *
 * <p>정리 규칙은 Application을 직접 불러 동기로 고정하고, 이벤트가 리스너에 닿는지는
 * 마지막 테스트 하나가 따로 확인한다. 리스너는 {@code @Async}라 결과를 기다려야 해서
 * 모든 규칙을 그 위에서 검증하면 테스트가 느리고 불안정해진다 — 관심사를 나눈다.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TeamTestFixture.class})
class TeamMembershipEndedIT {

    @Autowired
    TeamTestFixture fixture;

    @Autowired
    TeamService teamService;

    @Autowired
    TeamMemberService teamMemberService;

    @Autowired
    TeamMasterService teamMasterService;

    @Autowired
    CohortMembershipService membershipService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    /**
     * 마스터가 사라져도 팀은 남는다. 승격하지 않으면 MASTER 0명인 팀이 커밋되고,
     * 그 팀은 아무도 해체하거나 팀원을 관리할 수 없다 — 되살릴 API가 없다.
     */
    @Test
    @DisplayName("마스터의 소속이 종료되면 남은 팀원이 마스터가 된다")
    void promotesRemainingMemberWhenMasterMembershipEnds() {
        Long cohortId = fixture.createCohort("종료-위임");
        TeamTestFixture.Member master = fixture.createActiveMember(cohortId);
        TeamTestFixture.Member successor = fixture.createActiveMember(cohortId);
        Long teamId = teamService.create(cohortId, "종료-위임 팀", master.userId()).teamId();
        teamMemberService.addMember(teamId, successor.userId(), master.userId());

        assertThat(teamMasterService.removeEndedMember(master.membershipId())).isTrue();

        assertThat(roleOf(teamId, successor.membershipId())).isEqualTo("MASTER");
        assertThat(memberRows(teamId)).isEqualTo(1);
        assertThat(masterCount(teamId)).isEqualTo(1);
        assertThat(isDisbanded(teamId)).isFalse();
    }

    /** 마지막 한 명이 나가면 승격시킬 대상이 없다 — 팀이 사라져야 한다. */
    @Test
    @DisplayName("마지막 팀원의 소속이 종료되면 팀이 소프트 삭제된다")
    void disbandsTeamWhenLastMembershipEnds() {
        Long cohortId = fixture.createCohort("종료-해체");
        TeamTestFixture.Member master = fixture.createActiveMember(cohortId);
        Long teamId = teamService.create(cohortId, "종료-해체 팀", master.userId()).teamId();

        assertThat(teamMasterService.removeEndedMember(master.membershipId())).isTrue();

        assertThat(isDisbanded(teamId)).isTrue();
        assertThat(memberRows(teamId)).isZero();
    }

    /**
     * <b>재가입이 가능해야 한다.</b> 소속 행을 소프트 삭제하거나 남겨두면
     * {@code uq_team_members_membership}이 상태 조건 없는 유니크라 그 사람이 어떤 팀에도
     * 다시 들어갈 수 없다 — 물리 삭제여야 하는 이유다.
     */
    @Test
    @DisplayName("정리된 사람은 같은 기수의 다른 팀에 다시 들어갈 수 있다")
    void cleanedUpMemberCanJoinAnotherTeam() {
        Long cohortId = fixture.createCohort("종료-재가입");
        TeamTestFixture.Member master = fixture.createActiveMember(cohortId);
        TeamTestFixture.Member rejoining = fixture.createActiveMember(cohortId);
        TeamTestFixture.Member otherMaster = fixture.createActiveMember(cohortId);
        Long teamId = teamService.create(cohortId, "종료-재가입 팀", master.userId()).teamId();
        teamMemberService.addMember(teamId, rejoining.userId(), master.userId());
        Long otherTeamId = teamService.create(cohortId, "종료-재가입 팀2", otherMaster.userId()).teamId();

        teamMasterService.removeEndedMember(rejoining.membershipId());

        teamMemberService.addMember(otherTeamId, rejoining.userId(), otherMaster.userId());

        assertThat(roleOf(otherTeamId, rejoining.membershipId())).isEqualTo("MEMBER");
    }

    /**
     * 훅은 재전달된다. 두 번째 실행이 예외를 던지면 재시도가 영원히 실패하고,
     * 뒤이을 점유·알림 정리까지 함께 막힌다.
     */
    @Test
    @DisplayName("같은 소속을 두 번 정리해도 안전하다")
    void cleanupIsIdempotent() {
        Long cohortId = fixture.createCohort("종료-멱등");
        TeamTestFixture.Member master = fixture.createActiveMember(cohortId);
        TeamTestFixture.Member successor = fixture.createActiveMember(cohortId);
        Long teamId = teamService.create(cohortId, "종료-멱등 팀", master.userId()).teamId();
        teamMemberService.addMember(teamId, successor.userId(), master.userId());

        assertThat(teamMasterService.removeEndedMember(master.membershipId())).isTrue();
        assertThat(teamMasterService.removeEndedMember(master.membershipId())).isFalse();

        // 두 번째 호출이 승격을 되돌리거나 팀을 해체하지 않았는지 확인한다.
        assertThat(roleOf(teamId, successor.membershipId())).isEqualTo("MASTER");
        assertThat(masterCount(teamId)).isEqualTo(1);
        assertThat(isDisbanded(teamId)).isFalse();
    }

    /**
     * <b>배선 자체를 확인한다.</b> 정리 규칙이 아무리 맞아도 이벤트가 리스너에 닿지 않으면
     * 아무 일도 일어나지 않는다 — {@code @EnableAsync} 누락, 잘못된 {@code TransactionPhase},
     * 리스너가 빈으로 등록되지 않은 경우가 전부 조용히 실패한다.
     *
     * <p>{@code @Async}라 커밋 직후 다른 스레드에서 돌기 때문에 결과를 기다린다.
     * 정리 규칙 자체는 위 테스트들이 동기로 고정하므로, 여기서 보는 것은 "닿는가" 하나다.</p>
     */
    @Test
    @DisplayName("소속을 종료하면 이벤트가 팀 정리까지 이어진다")
    void membershipEndedEventReachesTeamCleanup() {
        Long cohortId = fixture.createCohort("종료-배선");
        TeamTestFixture.Member master = fixture.createActiveMember(cohortId);
        TeamTestFixture.Member successor = fixture.createActiveMember(cohortId);
        Long teamId = teamService.create(cohortId, "종료-배선 팀", master.userId()).teamId();
        teamMemberService.addMember(teamId, successor.userId(), master.userId());

        membershipService.end(master.membershipId());

        awaitUntil(() -> "MASTER".equals(roleOfOrNull(teamId, successor.membershipId())),
                "종료 이벤트가 팀 정리로 이어지지 않았습니다");
        assertThat(memberRows(teamId)).isEqualTo(1);
    }

    private void awaitUntil(BooleanSupplier condition, String message) {
        long deadline = System.currentTimeMillis() + 5_000L;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError(message);
    }

    /** 정리 도중에는 행이 없을 수 있어 단건 조회 대신 목록으로 읽는다. */
    private String roleOfOrNull(Long teamId, Long membershipId) {
        return jdbcTemplate.queryForList("""
                        SELECT role FROM learning_service.team_members
                         WHERE team_id = ? AND cohort_membership_id = ?
                        """, String.class, teamId, membershipId)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private String roleOf(Long teamId, Long membershipId) {
        return jdbcTemplate.queryForObject("""
                SELECT role FROM learning_service.team_members
                 WHERE team_id = ? AND cohort_membership_id = ?
                """, String.class, teamId, membershipId);
    }

    private int memberRows(Long teamId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM learning_service.team_members WHERE team_id = ?
                """, Integer.class, teamId);
        return count == null ? 0 : count;
    }

    private int masterCount(Long teamId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM learning_service.team_members
                 WHERE team_id = ? AND role = 'MASTER'
                """, Integer.class, teamId);
        return count == null ? 0 : count;
    }

    private boolean isDisbanded(Long teamId) {
        Boolean disbanded = jdbcTemplate.queryForObject("""
                SELECT deleted_at IS NOT NULL FROM learning_service.teams WHERE id = ?
                """, Boolean.class, teamId);
        return Boolean.TRUE.equals(disbanded);
    }
}
