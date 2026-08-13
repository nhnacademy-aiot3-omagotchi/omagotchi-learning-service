package site.omagotchi.learningservice.team.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.team.application.EndedMembershipSweep;
import site.omagotchi.learningservice.team.application.TeamMasterService;
import site.omagotchi.learningservice.team.application.TeamMemberService;
import site.omagotchi.learningservice.team.application.TeamService;
import site.omagotchi.learningservice.team.support.TeamTestFixture;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이벤트가 유실됐을 때의 복구 (ADR space-team/0013).
 *
 * <p><b>소속 종료를 SQL로 직접 만든다.</b> {@code CohortMembershipService#end}를 부르면
 * 리스너가 곧바로 정리해 버려 "이벤트가 유실된 상태"를 만들 수 없다. 여기서 재현하려는
 * 것은 정확히 그 상태 — <b>소속은 ENDED인데 팀원 행이 남아 있는</b> 상황이다.</p>
 *
 * <p>실제 PostgreSQL이 있어야 의미가 있다. 자동 위임과 팀 소프트 삭제가 지켜야 할 것이
 * {@code uq_team_members_one_master}와 팀 행 락이라 인메모리로는 검증되지 않는다.</p>
 *
 * <p>스케줄러는 {@code application-test.yaml}에서 꺼져 있어 배경 실행이 끼어들지 않는다 —
 * 스윕 동작 자체는 여기서 Application을 직접 불러 확인한다.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TeamTestFixture.class})
class EndedMembershipSweepIT {

    private static final int BATCH = 50;

    @Autowired
    TeamTestFixture fixture;

    @Autowired
    TeamService teamService;

    @Autowired
    TeamMemberService teamMemberService;

    @Autowired
    TeamMasterService teamMasterService;

    @Autowired
    EndedMembershipSweep endedMembershipSweep;

    @Autowired
    JdbcTemplate jdbcTemplate;

    /**
     * 스윕의 존재 이유 — 리스너가 실행되지 않아도 결국 정리된다.
     */
    @Test
    @DisplayName("리스너가 실행되지 않은 종료 소속을 스윕이 정리한다")
    void cleansOrphanLeftByLostEvent() {
        Long cohortId = fixture.createCohort("스윕-기본");
        TeamTestFixture.Member master = fixture.createActiveMember(cohortId);
        TeamTestFixture.Member member = fixture.createActiveMember(cohortId);
        Long teamId = teamService.create(cohortId, "스윕-기본 팀", master.userId()).teamId();
        teamMemberService.addMember(teamId, member.userId(), master.userId());

        endMembershipWithoutEvent(member.membershipId());
        assertThat(memberRows(teamId)).isEqualTo(2);

        assertThat(endedMembershipSweep.sweep(BATCH)).isEqualTo(1);

        assertThat(memberRows(teamId)).isEqualTo(1);
        assertThat(roleOfOrNull(teamId, member.membershipId())).isNull();
    }

    /**
     * 가장 심각한 케이스 — 고아가 MASTER면 팀이 영구히 잠긴다.
     *
     * <p>멤버십이 ENDED라 본인도 403이고, {@code uq_team_members_one_master}가 자리를
     * 점유해 자동 위임이 막히며, 해체도 MASTER만 가능해 아무도 손댈 수 없다.</p>
     */
    @Test
    @DisplayName("고아가 마스터면 자동 위임까지 이어진다")
    void promotesSuccessorWhenOrphanIsMaster() {
        Long cohortId = fixture.createCohort("스윕-위임");
        TeamTestFixture.Member master = fixture.createActiveMember(cohortId);
        TeamTestFixture.Member successor = fixture.createActiveMember(cohortId);
        Long teamId = teamService.create(cohortId, "스윕-위임 팀", master.userId()).teamId();
        teamMemberService.addMember(teamId, successor.userId(), master.userId());

        endMembershipWithoutEvent(master.membershipId());

        assertThat(endedMembershipSweep.sweep(BATCH)).isEqualTo(1);

        assertThat(roleOf(teamId, successor.membershipId())).isEqualTo("MASTER");
        assertThat(masterCount(teamId)).isEqualTo(1);
        assertThat(isDisbanded(teamId)).isFalse();
    }

    @Test
    @DisplayName("고아가 마지막 팀원이면 팀 소프트 삭제까지 이어진다")
    void disbandsTeamWhenOrphanIsLastMember() {
        Long cohortId = fixture.createCohort("스윕-해체");
        TeamTestFixture.Member master = fixture.createActiveMember(cohortId);
        Long teamId = teamService.create(cohortId, "스윕-해체 팀", master.userId()).teamId();

        endMembershipWithoutEvent(master.membershipId());

        assertThat(endedMembershipSweep.sweep(BATCH)).isEqualTo(1);

        assertThat(memberRows(teamId)).isZero();
        assertThat(isDisbanded(teamId)).isTrue();
    }

    /**
     * 이 스윕의 최악 실패는 정리를 못 하는 것이 아니라 <b>정상 팀원을 지우는 것</b>이다.
     */
    @Test
    @DisplayName("활성 소속의 팀원은 건드리지 않는다")
    void leavesActiveMembersUntouched() {
        Long cohortId = fixture.createCohort("스윕-무해");
        TeamTestFixture.Member master = fixture.createActiveMember(cohortId);
        TeamTestFixture.Member member = fixture.createActiveMember(cohortId);
        Long teamId = teamService.create(cohortId, "스윕-무해 팀", master.userId()).teamId();
        teamMemberService.addMember(teamId, member.userId(), master.userId());

        assertThat(endedMembershipSweep.sweep(BATCH)).isZero();

        assertThat(memberRows(teamId)).isEqualTo(2);
        assertThat(roleOf(teamId, master.membershipId())).isEqualTo("MASTER");
        assertThat(isDisbanded(teamId)).isFalse();
    }

    /**
     * 스케줄러는 주기마다 다시 돈다. 두 번째 실행이 자동 위임을 또 하거나 팀을 다시
     * 해체하려 들면 안 된다.
     */
    @Test
    @DisplayName("두 번 실행해도 자동 위임과 팀 해체가 중복 적용되지 않는다")
    void isIdempotentAcrossRuns() {
        Long cohortId = fixture.createCohort("스윕-멱등");
        TeamTestFixture.Member master = fixture.createActiveMember(cohortId);
        TeamTestFixture.Member successor = fixture.createActiveMember(cohortId);
        Long teamId = teamService.create(cohortId, "스윕-멱등 팀", master.userId()).teamId();
        teamMemberService.addMember(teamId, successor.userId(), master.userId());
        endMembershipWithoutEvent(master.membershipId());

        assertThat(endedMembershipSweep.sweep(BATCH)).isEqualTo(1);
        // 두 번째 실행에는 정리할 것이 없다 — 고아 행이 이미 사라졌기 때문이다.
        assertThat(endedMembershipSweep.sweep(BATCH)).isZero();

        assertThat(roleOf(teamId, successor.membershipId())).isEqualTo("MASTER");
        assertThat(masterCount(teamId)).isEqualTo(1);
        assertThat(memberRows(teamId)).isEqualTo(1);
        assertThat(isDisbanded(teamId)).isFalse();
    }

    /**
     * 리스너와 스윕이 같은 대상을 동시에 집을 수 있다 (ADR 0013 §6).
     *
     * <p>{@code removeEndedMember}가 {@code findByIdForUpdate}로 팀 행을 잠그는 것이 안전의
     * 근거인데, 근거를 주장으로만 두지 않고 여기서 고정한다. 리스너 경로는 그 Method를
     * 직접 부르는 것으로 대신한다 — {@code @Async} 리스너를 그대로 쓰면 두 실행이 실제로
     * 겹쳤는지 보장할 수 없다.</p>
     *
     * <p>어느 쪽이 이기든 결과는 하나여야 한다: 승격은 한 번, MASTER는 정확히 1명.</p>
     */
    @Test
    @DisplayName("리스너와 스윕이 동시에 처리해도 결과가 하나다")
    void listenerAndSweepAreSafeConcurrently() throws InterruptedException {
        Long cohortId = fixture.createCohort("스윕-동시");
        TeamTestFixture.Member master = fixture.createActiveMember(cohortId);
        TeamTestFixture.Member successor = fixture.createActiveMember(cohortId);
        Long teamId = teamService.create(cohortId, "스윕-동시 팀", master.userId()).teamId();
        teamMemberService.addMember(teamId, successor.userId(), master.userId());
        endMembershipWithoutEvent(master.membershipId());

        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger cleaned = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        List<Runnable> paths = List.of(
                () -> {
                    if (teamMasterService.removeEndedMember(master.membershipId())) {
                        cleaned.incrementAndGet();
                    }
                },
                () -> cleaned.addAndGet(endedMembershipSweep.sweep(BATCH))
        );
        for (Runnable path : paths) {
            executor.submit(() -> {
                try {
                    start.await();
                    path.run();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        start.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(20, TimeUnit.SECONDS)).isTrue();

        // 두 경로가 겹쳐도 실제 정리는 한 번만 성립한다.
        assertThat(cleaned.get()).isEqualTo(1);
        assertThat(roleOf(teamId, successor.membershipId())).isEqualTo("MASTER");
        assertThat(masterCount(teamId)).isEqualTo(1);
        assertThat(memberRows(teamId)).isEqualTo(1);
    }

    /**
     * 배치보다 많은 행이 있어도 커서로 끝까지 순회한다.
     *
     * <p>조회 대상이 고아가 아니라 전체 소속 행이라, 커서가 없으면 앞쪽 배치만 반복해서
     * 보고 뒤쪽 고아에 영원히 닿지 못한다.</p>
     */
    @Test
    @DisplayName("배치 크기를 넘는 행이 있어도 커서로 전부 순회한다")
    void walksBeyondSingleBatch() {
        Long cohortId = fixture.createCohort("스윕-커서");
        TeamTestFixture.Member master = fixture.createActiveMember(cohortId);
        Long teamId = teamService.create(cohortId, "스윕-커서 팀", master.userId()).teamId();

        TeamTestFixture.Member last = null;
        for (int i = 0; i < 4; i++) {
            last = fixture.createActiveMember(cohortId);
            teamMemberService.addMember(teamId, last.userId(), master.userId());
        }
        // 가장 마지막에 추가된 행 = team_members.id가 가장 큰 행만 고아로 만든다.
        endMembershipWithoutEvent(last.membershipId());

        // 배치 1이면 커서 없이는 첫 행만 반복해서 보고 끝난다.
        assertThat(endedMembershipSweep.sweep(1)).isEqualTo(1);

        assertThat(memberRows(teamId)).isEqualTo(4);
        assertThat(roleOfOrNull(teamId, last.membershipId())).isNull();
    }

    /**
     * 이벤트를 거치지 않고 소속만 종료시킨다 — "정리가 유실된 상태"의 재현이다.
     *
     * <p>{@code ck_cohort_memberships_processed}가 {@code status <> 'PENDING'}인 행에
     * {@code processed_at}을 요구하므로, fixture가 만든 ACTIVE 행은 그 값을 이미 갖고 있다.</p>
     */
    private void endMembershipWithoutEvent(Long membershipId) {
        int updated = jdbcTemplate.update("""
                UPDATE learning_service.cohort_memberships
                   SET status = 'ENDED', ended_at = now()
                 WHERE id = ? AND status = 'ACTIVE'
                """, membershipId);
        assertThat(updated).isEqualTo(1);
    }

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
