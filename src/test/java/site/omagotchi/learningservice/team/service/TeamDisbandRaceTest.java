package site.omagotchi.learningservice.team.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.team.application.TeamAccessSupport;
import site.omagotchi.learningservice.team.application.TeamService;
import site.omagotchi.learningservice.team.application.dto.command.CreateTeamRequest;
import site.omagotchi.learningservice.team.domain.Team;
import site.omagotchi.learningservice.team.domain.TeamErrorCode;
import site.omagotchi.learningservice.team.infrastructure.TeamRepository;
import site.omagotchi.learningservice.team.support.TeamTestFixture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 해체 레이스 회귀 테스트.
 *
 * lockActiveTeam은 SELECT ... FOR UPDATE로 행을 잡은 뒤 deleted_at을 재확인해
 * "해체 커밋 직후 도착한 요청"을 404로 잡는 것이 설계 의도다. 그런데 같은
 * 트랜잭션에서 Team을 미리 엔티티로 읽어두면 Hibernate가 1차 캐시의 인스턴스를
 * 그대로 돌려주기 때문에 재확인이 락 이전 스냅샷을 보고 통과해버린다.
 *
 * 이 테스트는 락 전 조회가 엔티티를 만들지 않는다는 것을 고정한다.
 * 회귀(= 락 전에 loadActiveTeam을 부르는 코드)가 들어오면 실패한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TeamTestFixture.class})
class TeamDisbandRaceTest {

    @Autowired
    TeamTestFixture fixture;

    @Autowired
    TeamService teamService;

    @Autowired
    TeamAccessSupport accessSupport;

    @Autowired
    TeamRepository teamRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("락 전에 기수를 조회했어도, 그 사이 해체가 커밋되면 락 시점에 404로 잡힌다")
    void test1() {
        Long cohortId = fixture.createCohort("1기");
        var master = fixture.createActiveMember(cohortId);
        var team = teamService.create(new CreateTeamRequest(cohortId, "오마고치"), master.userId());
        Long teamId = team.teamId();

        Throwable thrown = transactionTemplate.execute(status -> {
            // 팀원 추가가 락 전에 수행하는 검증 단계와 같은 조회다.
            assertThat(accessSupport.requireActiveTeamCohortId(teamId)).isEqualTo(cohortId);

            // 이 트랜잭션이 열려 있는 동안 다른 트랜잭션이 해체하고 커밋한다.
            disbandInSeparateTransaction(teamId);

            return catchThrowable(() -> accessSupport.lockActiveTeam(teamId));
        });

        assertThat(thrown)
                .hasFieldOrPropertyWithValue("errorCode", TeamErrorCode.TEAM_NOT_FOUND);
    }

    /**
     * [대조군] 락 전에 Team을 엔티티로 미리 읽어두면 무슨 일이 생기는지 고정해둔다.
     *
     * requireActiveTeamCohortId 대신 loadActiveTeam(스칼라가 아니라 엔티티 조회)을 먼저 호출하면,
     * 그 Team 인스턴스가 트랜잭션의 1차 캐시에 올라간다. 이후 lockActiveTeam이 SELECT ... FOR UPDATE를
     * 실제로 실행해도 Hibernate는 새로 읽은 컬럼값으로 필드를 덮어쓰지 않고 캐시된 인스턴스를 그대로
     * 반환한다. 그래서 해체가 그 사이 커밋됐어도 isDisbanded()는 락 이전 스냅샷(false)을 보고,
     * 예외 없이 통과해버린다 — 이 테스트는 그 실패를 재현해 고정한 것이다 (thrown이 null이어야 통과).
     *
     * test1과 이 테스트의 유일한 차이는 63번째 줄에 해당하는 사전 조회 방식뿐이다.
     */
    @Test
    @DisplayName("[대조군] 락 전에 loadActiveTeam으로 엔티티를 미리 읽으면, 해체가 커밋돼도 캐시된 인스턴스가 반환되어 재확인이 무력화된다")
    void test2() {
        Long cohortId = fixture.createCohort("1기");
        var master = fixture.createActiveMember(cohortId);
        var team = teamService.create(new CreateTeamRequest(cohortId, "오마고치"), master.userId());
        Long teamId = team.teamId();

        Throwable thrown = transactionTemplate.execute(status -> {
            // 버그가 있던 방식: 락 전에 Team을 엔티티로 미리 읽는다.
            accessSupport.loadActiveTeam(teamId);

            // 이 트랜잭션이 열려 있는 동안 다른 트랜잭션이 해체하고 커밋한다.
            disbandInSeparateTransaction(teamId);

            return catchThrowable(() -> accessSupport.lockActiveTeam(teamId));
        });

        // 1차 캐시 함정 때문에 예외가 발생하지 않는다 — TEAM_NOT_FOUND를 기대하면 이 테스트가 실패한다.
        assertThat(thrown).isNull();
    }

    /**
     * 새 스레드에서 실행해야 바깥 트랜잭션에 묶이지 않는다.
     * 같은 스레드에서 REQUIRES_NEW로 열면 커넥션 두 개를 동시에 점유해
     * 풀 크기에 따라 교착 위험이 있다.
     */
    private void disbandInSeparateTransaction(Long teamId) {
        Thread other = new Thread(() -> transactionTemplate.executeWithoutResult(status -> {
            Team target = teamRepository.findById(teamId).orElseThrow();
            target.disband();
            teamRepository.saveAndFlush(target);
        }));
        other.start();
        try {
            other.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
