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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willAnswer;

/**
 * 팀원 추가와 마스터 위임의 레이스 회귀 테스트.
 *
 * <p>{@code TeamMemberService.addMember}는 쓰기 트랜잭션 밖에서 {@code requireMaster}로 빠르게
 * 실패한 뒤 Identity 계정을 검증하고, 별도 쓰기 작업이 {@code teams} 행 락을 잡은 상태에서
 * 현재 MASTER를 다시 확인해야 한다. 재확인이 없으면 외부 호출 중 다른 트랜잭션이
 * 위임(delegate)을 커밋해 요청자가 이미 MEMBER가 됐어도 그대로 팀원이 추가된다.</p>
 *
 * <p><b>실제 {@code addMember}를 호출하는 것이 이 테스트의 요점이다.</b> 내부 단계를
 * 손으로 재현하면 쓰기 작업의 잠금 이후 검증만 확인하게 되어, 정작
 * {@code addMember}가 쓰기 작업을 호출하지 않아도 테스트가 통과할 수 있다.</p>
 *
 * <p>교차 순서는 스레드 타이밍이 아니라 <b>주입 지점</b>으로 결정적으로 만든다.
 * {@code addMember}의 실행 순서가
 * {@code requireMaster → validateAccount → TeamMemberAddition.add}이므로,
 * {@link IdentityAccountClient#getState}가 정확히 "Identity 조회 전 접근 제어 이후, 락 획득 이전"이다.
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
     * Identity 조회 전 접근 제어를 통과한 요청자가 락을 잡기 전 사이에 위임으로 MEMBER가 되면,
     * 락 이후 재검증이 잡아내고 팀원은 추가되지 않아야 한다.
     */
    @Test
    @DisplayName("Identity 조회 전 접근 제어 이후 위임되면 팀원 추가가 403으로 거부된다")
    void addMemberRejectedWhenDelegationCommitsBeforeLock() {
        // Given: 위임 가능한 팀과 Identity 조회 중 위임을 커밋하는 응답
        Long cohortId = fixture.createCohort("레이스 기수");
        TeamTestFixture.Member master = fixture.createActiveMember(cohortId);
        TeamTestFixture.Member successor = fixture.createActiveMember(cohortId);
        TeamTestFixture.Member outsider = fixture.createActiveMember(cohortId);
        Long teamId = teamService.create(cohortId, "레이스 팀", master.userId()).teamId();

        // delegate의 대상이 될 수 있도록 successor를 먼저 정상적으로 팀원에 넣는다.
        teamMemberService.addMember(teamId, successor.userId(), master.userId());
        Long successorMemberId = memberIdOf(teamId, successor.membershipId());

        // addMember가 Identity 조회 전 접근 제어를 마치고 락을 잡기 직전에 위임을 커밋시킨다.
        willAnswer(invocation -> {
            teamMasterService.delegate(teamId, successorMemberId, master.userId());
            return IdentityAccountState.ACTIVE;
        }).given(identityAccountClient).getState(any());

        // When & Then: 위임으로 권한을 잃은 이전 MASTER의 추가 요청 거부
        assertThatThrownBy(() ->
                teamMemberService.addMember(teamId, outsider.userId(), master.userId()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(TeamErrorCode.MASTER_REQUIRED);

        // Then: 대상은 추가되지 않고 위임 결과만 유지
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

}
