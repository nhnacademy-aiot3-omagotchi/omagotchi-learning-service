package site.omagotchi.learningservice.team.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.team.application.TeamErrorCode;
import site.omagotchi.learningservice.team.application.TeamMasterService;
import site.omagotchi.learningservice.team.application.TeamMemberService;
import site.omagotchi.learningservice.team.application.TeamService;
import site.omagotchi.learningservice.team.application.dto.command.AddTeamMemberRequest;
import site.omagotchi.learningservice.team.application.dto.command.CreateTeamRequest;
import site.omagotchi.learningservice.team.support.TeamTestFixture;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 마스터 불변식의 동시성 방어 (GR-12, GR-19, GR-20).
 *
 * <p>단위 테스트로 검증할 수 없는 것만 여기 둔다. {@code uq_team_members_one_master}는
 * 부분 유니크라 실제 DB가 있어야 위반이 재현되고, 데드락도 마찬가지다.</p>
 *
 * <p><b>DB는 "최대 1명"만 보장한다.</b> "최소 1명"은 트랜잭션 책임이라, 어떤 인터리빙에서도
 * MASTER가 0명이 되지 않는지가 이 테스트의 핵심이다.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TeamTestFixture.class})
class TeamMasterIT {

    @Autowired
    TeamTestFixture fixture;

    @Autowired
    TeamService teamService;

    @Autowired
    TeamMemberService teamMemberService;

    @Autowired
    TeamMasterService teamMasterService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("위임하면 두 역할이 교환되고 MASTER는 정확히 1명이다.")
    void test1() {
        Long cohortId = fixture.createCohort("위임 기수");
        var master = fixture.createActiveMember(cohortId);
        var target = fixture.createActiveMember(cohortId);
        Long teamId = createTeamWith(cohortId, "위임 팀", master, target);

        teamMasterService.delegate(teamId, memberIdOf(teamId, target.membershipId()), master.userId());

        assertThat(masterCount(teamId)).isEqualTo(1);
        assertThat(roleOf(teamId, target.membershipId())).isEqualTo("MASTER");
        assertThat(roleOf(teamId, master.membershipId())).isEqualTo("MEMBER");
    }

    /**
     * 명세 §5: "탈퇴가 먼저면 위임 409, 위임이 먼저면 대상이 MASTER가 되어 탈퇴가
     * '위임 먼저' 409". 어느 쪽이든 MASTER는 정확히 1명이어야 한다.
     */
    @Test
    @DisplayName("위임과 대상 탈퇴가 동시에 와도 MASTER는 정확히 1명이다.")
    void test2() throws Exception {
        Long cohortId = fixture.createCohort("위임-탈퇴 기수");
        var master = fixture.createActiveMember(cohortId);
        var target = fixture.createActiveMember(cohortId);
        Long teamId = createTeamWith(cohortId, "위임-탈퇴 팀", master, target);
        Long targetMemberId = memberIdOf(teamId, target.membershipId());

        runTogether(
                () -> catchThrowable(() ->
                        teamMasterService.delegate(teamId, targetMemberId, master.userId())),
                () -> catchThrowable(() ->
                        teamMemberService.leave(teamId, target.userId()))
        );

        // 팀이 남아 있다면 MASTER는 반드시 1명이다 — 0명이면 아무도 관리할 수 없다.
        assertThat(masterCount(teamId)).isEqualTo(1);
    }

    /**
     * {@code lockAllByTeamId}가 id 오름차순으로 잠그지 않으면 두 위임이 반대 순서로
     * 행을 잡아 데드락이 난다. 둘 다 끝나는 것 자체가 검증이다.
     */
    @Test
    @DisplayName("위임 두 건이 동시에 와도 데드락이 나지 않는다.")
    void test3() throws Exception {
        Long cohortId = fixture.createCohort("이중 위임 기수");
        var master = fixture.createActiveMember(cohortId);
        var first = fixture.createActiveMember(cohortId);
        var second = fixture.createActiveMember(cohortId);
        Long teamId = createTeamWith(cohortId, "이중 위임 팀", master, first, second);

        Long firstMemberId = memberIdOf(teamId, first.membershipId());
        Long secondMemberId = memberIdOf(teamId, second.membershipId());

        List<Throwable> results = runTogether(
                () -> catchThrowable(() ->
                        teamMasterService.delegate(teamId, firstMemberId, master.userId())),
                () -> catchThrowable(() ->
                        teamMasterService.delegate(teamId, secondMemberId, master.userId()))
        );

        // 첫 커밋 후 요청자는 MEMBER라 두 번째는 권한 검증에서 막힌다 (명세 §5 "이중 위임").
        assertThat(results).filteredOn(thrown -> thrown == null).hasSize(1);
        assertThat(masterCount(teamId)).isEqualTo(1);
    }

    @Test
    @DisplayName("해체하면 팀원 행이 사라지고 팀은 조회에서 빠진다.")
    void test4() {
        Long cohortId = fixture.createCohort("해체 기수");
        var master = fixture.createActiveMember(cohortId);
        var member = fixture.createActiveMember(cohortId);
        Long teamId = createTeamWith(cohortId, "해체 팀", master, member);

        teamMasterService.disband(teamId, master.userId());

        assertThat(memberCount(teamId)).isZero();
        assertThat(teamService.getMyTeams(master.userId())).isEmpty();
    }

    /** {@code uq_teams_active_name}이 {@code WHERE deleted_at IS NULL}이라 동명 재생성이 된다. */
    @Test
    @DisplayName("해체한 팀과 같은 이름으로 다시 만들 수 있다.")
    void test5() {
        Long cohortId = fixture.createCohort("동명 재생성 기수");
        var master = fixture.createActiveMember(cohortId);
        Long teamId = createTeamWith(cohortId, "같은이름", master);

        teamMasterService.disband(teamId, master.userId());

        var other = fixture.createActiveMember(cohortId);
        Long recreatedId = createTeamWith(cohortId, "같은이름", other);

        assertThat(recreatedId).isNotEqualTo(teamId);
    }

    /**
     * 완료 조건 1번. 강등을 flush하지 않고 승격하면 순간적으로 MASTER가 2명이 되어
     * 부분 유니크가 막는다 — 그 방어가 실제로 걸리는지 직접 확인한다.
     */
    @Test
    @DisplayName("MASTER를 둘로 만들려 하면 부분 유니크가 막는다.")
    void test6() {
        Long cohortId = fixture.createCohort("역순 방어 기수");
        var master = fixture.createActiveMember(cohortId);
        var target = fixture.createActiveMember(cohortId);
        Long teamId = createTeamWith(cohortId, "역순 방어 팀", master, target);

        Throwable thrown = catchThrowable(() -> jdbcTemplate.update("""
                UPDATE learning_service.team_members SET role = 'MASTER'
                 WHERE team_id = ? AND cohort_membership_id = ?
                """, teamId, target.membershipId()));

        assertThat(thrown).isNotNull();
        assertThat(masterCount(teamId)).isEqualTo(1);
    }

    // ────────────────────────────── 헬퍼 ──────────────────────────────

    /** 첫 사람이 MASTER가 되고 나머지는 팀원으로 추가된다. */
    private Long createTeamWith(Long cohortId, String name, TeamTestFixture.Member... members) {
        Long teamId = teamService.create(
                new CreateTeamRequest(cohortId, name), members[0].userId()).teamId();

        for (int i = 1; i < members.length; i++) {
            teamMemberService.addMember(
                    teamId, new AddTeamMemberRequest(members[i].userId()), members[0].userId());
        }
        return teamId;
    }

    /** 두 작업을 래치로 묶어 동시에 출발시킨다. 순차 실행하면 경합 경로를 지나지 않는다. */
    private List<Throwable> runTogether(Callable<Throwable> first, Callable<Throwable> second)
            throws Exception {

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> a = pool.submit(() -> {
                start.await();
                return first.call();
            });
            Future<Throwable> b = pool.submit(() -> {
                start.await();
                return second.call();
            });
            start.countDown();

            List<Throwable> results = new ArrayList<>();
            results.add(a.get(30, TimeUnit.SECONDS));
            results.add(b.get(30, TimeUnit.SECONDS));
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    private int masterCount(Long teamId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM learning_service.team_members
                 WHERE team_id = ? AND role = 'MASTER'
                """, Integer.class, teamId);
        return count == null ? 0 : count;
    }

    private int memberCount(Long teamId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM learning_service.team_members WHERE team_id = ?",
                Integer.class, teamId);
        return count == null ? 0 : count;
    }

    private String roleOf(Long teamId, Long membershipId) {
        return jdbcTemplate.queryForObject("""
                SELECT role FROM learning_service.team_members
                 WHERE team_id = ? AND cohort_membership_id = ?
                """, String.class, teamId, membershipId);
    }

    private Long memberIdOf(Long teamId, Long membershipId) {
        return jdbcTemplate.queryForObject("""
                SELECT id FROM learning_service.team_members
                 WHERE team_id = ? AND cohort_membership_id = ?
                """, Long.class, teamId, membershipId);
    }
}
