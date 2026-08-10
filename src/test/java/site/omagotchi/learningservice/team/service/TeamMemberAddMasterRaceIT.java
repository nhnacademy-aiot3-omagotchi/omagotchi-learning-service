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
import site.omagotchi.learningservice.team.application.TeamErrorCode;
import site.omagotchi.learningservice.team.application.TeamMasterService;
import site.omagotchi.learningservice.team.application.TeamMembership;
import site.omagotchi.learningservice.team.application.TeamMemberService;
import site.omagotchi.learningservice.team.application.TeamService;
import site.omagotchi.learningservice.team.application.port.TeamMemberRepository;
import site.omagotchi.learningservice.team.domain.TeamMember;
import site.omagotchi.learningservice.team.support.TeamTestFixture;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * 팀원 추가와 마스터 위임의 레이스 회귀 테스트.
 *
 * <p>{@code TeamMemberService.addMember}는 락 밖에서 {@code requireMaster}로 빠르게
 * 실패하고, {@code teams} 행 락을 잡은 뒤 같은 검증을 한 번 더 해야 한다 —
 * {@code TeamAccessSupport.requireMaster}의 계약이 그렇다("반드시 teams 행 락을 잡은
 * 뒤에 호출해야 의미가 있다"). 재확인이 없으면, 락 밖 검사와 락 획득 사이에 다른
 * 트랜잭션이 위임(delegate)을 커밋해 요청자가 이미 MEMBER가 됐어도 그대로 팀원이
 * 추가된다.</p>
 *
 * <p>{@code TeamDisbandRaceIT}와 같은 기법을 쓴다 — 실제 스레드 타이밍에 기대지 않고
 * {@link TransactionTemplate}으로 "락 밖 검증 → (다른 트랜잭션이 위임 커밋) → 락 획득
 * 후 재검증"의 교차 순서를 결정적으로 재현한다.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TeamTestFixture.class})
class TeamMemberAddMasterRaceIT {

    @Autowired
    TeamTestFixture fixture;

    @Autowired
    TeamService teamService;

    @Autowired
    TeamMemberService teamMemberService;

    @Autowired
    TeamMasterService teamMasterService;

    @Autowired
    TeamAccessSupport accessSupport;

    @Autowired
    TeamMemberRepository teamMemberRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    /**
     * 락 전 검증을 통과한 요청자가, 락을 잡기 전 사이에 위임으로 MEMBER가 되면
     * 락 이후 재검증에서 잡혀야 한다.
     */
    @Test
    @DisplayName("팀원 추가 락 밖 검증 이후 위임이 먼저 커밋되면, 락 이후 재검증이 MASTER_REQUIRED로 잡는다")
    void test1() {
        Long cohortId = fixture.createCohort("레이스 기수");
        TeamTestFixture.Member master = fixture.createActiveMember(cohortId);
        TeamTestFixture.Member successor = fixture.createActiveMember(cohortId);
        Long teamId = teamService.create(cohortId, "레이스 팀", master.userId()).teamId();

        // delegate의 대상이 될 수 있도록 successor를 먼저 정상적으로 팀원에 넣는다.
        teamMemberService.addMember(teamId, successor.userId(), master.userId());
        Long successorMemberId = teamMemberRepository
                .findByTeamIdAndCohortMembershipId(teamId, successor.membershipId())
                .map(TeamMember::getId)
                .orElseThrow();

        Throwable thrown = transactionTemplate.execute(status -> {
            // addMember의 57행과 같은 락 밖 사전 검증. 이 시점엔 master가 아직 MASTER라 통과한다.
            Long resolvedCohortId = accessSupport.requireActiveTeamCohortId(teamId);
            TeamMembership requestMembership =
                    accessSupport.requireActiveMembership(resolvedCohortId, master.userId());
            accessSupport.requireMaster(teamId, requestMembership.id());

            // 이 트랜잭션이 열려 있는 동안 다른 트랜잭션이 위임을 커밋한다 — master는 이제 MEMBER다.
            delegateInSeparateTransaction(teamId, successorMemberId, master.userId());

            // addMember의 74행과 같은 락 획득.
            accessSupport.lockActiveTeam(teamId);

            // 수정본이 여기서 다시 확인해야 하는 지점. requireMaster를 그대로 다시 부르면
            // 위 사전 검증이 이미 캐시해 둔 TeamMember 인스턴스가 그대로 반환되어 위임을
            // 못 본다 — 그래서 값 기반의 requireStillMaster를 검증한다.
            return catchThrowable(() -> accessSupport.requireStillMaster(teamId, requestMembership.id()));
        });

        assertThat(thrown)
                .hasFieldOrPropertyWithValue("errorCode", TeamErrorCode.MASTER_REQUIRED);
    }

    /**
     * 새 스레드에서 실행해야 바깥 트랜잭션에 묶이지 않는다.
     * 같은 스레드에서 REQUIRES_NEW로 열면 커넥션 두 개를 동시에 점유해
     * 풀 크기에 따라 교착 위험이 있다 (TeamDisbandRaceIT와 같은 이유).
     */
    private void delegateInSeparateTransaction(Long teamId, Long targetMemberId, UUID requesterUserId) {
        Thread other = new Thread(() -> teamMasterService.delegate(teamId, targetMemberId, requesterUserId));
        other.start();
        try {
            other.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
