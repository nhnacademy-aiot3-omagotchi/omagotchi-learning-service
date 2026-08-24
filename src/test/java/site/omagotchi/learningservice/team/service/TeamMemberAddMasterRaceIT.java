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
import site.omagotchi.learningservice.team.domain.TeamMember;
import site.omagotchi.learningservice.team.domain.TeamMemberRole;
import site.omagotchi.learningservice.team.support.TeamTestFixture;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willAnswer;

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
 * <p><b>실제 {@code addMember}를 호출하는 것이 이 테스트의 요점이다.</b> 내부 단계를
 * 손으로 재현하면 {@code requireStillMaster}의 동작만 확인하게 되어, 정작
 * {@code addMember}에서 그 호출을 지워도 테스트가 통과한다.</p>
 *
 * <p>교차 순서는 스레드 타이밍이 아니라 <b>주입 지점</b>으로 결정적으로 만든다.
 * {@code addMember}의 실행 순서가
 * {@code requireMaster → validateAccount → lockActiveTeam → requireStillMaster}이므로,
 * {@link IdentityAccountClient#getState}가 정확히 "락 밖 검증 이후, 락 획득 이전"이다.
 * 그 안에서 위임을 커밋시키면 재현하려는 창이 그대로 열린다.</p>
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
    TeamMemberRepository teamMemberRepository;

    @MockitoSpyBean
    IdentityAccountClient identityAccountClient;

    /**
     * 락 전 검증을 통과한 요청자가 락을 잡기 전 사이에 위임으로 MEMBER가 되면,
     * 락 이후 재검증이 잡아내고 팀원은 추가되지 않아야 한다.
     */
    @Test
    @DisplayName("락 밖 검증 이후 위임이 먼저 커밋되면 팀원 추가가 403으로 거부된다")
    void addMemberRejectedWhenDelegationCommitsBeforeLock() {
        Long cohortId = fixture.createCohort("레이스 기수");
        TeamTestFixture.Member master = fixture.createActiveMember(cohortId);
        TeamTestFixture.Member successor = fixture.createActiveMember(cohortId);
        TeamTestFixture.Member outsider = fixture.createActiveMember(cohortId);
        Long teamId = teamService.create(cohortId, "레이스 팀", master.userId()).teamId();

        // delegate의 대상이 될 수 있도록 successor를 먼저 정상적으로 팀원에 넣는다.
        teamMemberService.addMember(teamId, successor.userId(), master.userId());
        Long successorMemberId = memberIdOf(teamId, successor.membershipId());

        // addMember가 락 밖 검증을 마치고 락을 잡기 직전에 위임을 커밋시킨다.
        // 한 번만 끼어들어야 한다 — 위 addMember 호출에도 이 스텁이 걸리면 안 되고,
        // 재시도가 있어도 위임이 두 번 일어나면 안 된다.
        AtomicBoolean delegated = new AtomicBoolean(false);
        willAnswer(invocation -> {
            if (delegated.compareAndSet(false, true)) {
                delegateInSeparateTransaction(teamId, successorMemberId, master.userId());
            }
            return IdentityAccountState.ACTIVE;
        }).given(identityAccountClient).getState(any());

        assertThatThrownBy(() ->
                teamMemberService.addMember(teamId, outsider.userId(), master.userId()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(TeamErrorCode.MASTER_REQUIRED);

        assertThat(delegated).isTrue();

        // 무효화된 권한으로 팀원이 들어가지 않았는지 확인한다 — 예외만 보면
        // "던지긴 했는데 행은 남는" 경우를 놓친다.
        assertThat(teamMemberRepository
                .findByTeamIdAndCohortMembershipId(teamId, outsider.membershipId()))
                .isEmpty();

        // 위임 자체는 정상적으로 커밋됐어야 한다. 이게 아니면 레이스가 재현되지 않은 것이고,
        // 위 403은 다른 이유로 나온 셈이라 테스트가 의미를 잃는다.
        assertThat(roleOf(teamId, successor.membershipId())).isEqualTo(TeamMemberRole.MASTER);
        assertThat(roleOf(teamId, master.membershipId())).isEqualTo(TeamMemberRole.MEMBER);
    }

    private Long memberIdOf(Long teamId, Long membershipId) {
        return teamMemberRepository.findByTeamIdAndCohortMembershipId(teamId, membershipId)
                .map(TeamMember::getId)
                .orElseThrow();
    }

    private TeamMemberRole roleOf(Long teamId, Long membershipId) {
        return teamMemberRepository.findByTeamIdAndCohortMembershipId(teamId, membershipId)
                .map(TeamMember::getRole)
                .orElseThrow();
    }

    /**
     * 새 스레드에서 실행해야 바깥 트랜잭션에 묶이지 않는다.
     * 같은 스레드에서 REQUIRES_NEW로 열면 커넥션 두 개를 동시에 점유해
     * 풀 크기에 따라 교착 위험이 있다 (TeamDisbandRaceIT와 같은 이유).
     *
     * <p>{@code addMember}가 아직 {@code teams} 락을 잡기 전에 호출되므로 이 위임은
     * 대기 없이 락을 얻고 커밋한다 — 순서가 뒤바뀌면 교착이다.</p>
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
