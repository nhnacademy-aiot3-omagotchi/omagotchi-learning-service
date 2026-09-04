package site.omagotchi.learningservice.team.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.team.application.TeamMemberService;
import site.omagotchi.learningservice.team.application.TeamService;
import site.omagotchi.learningservice.team.application.port.IdentityAccountClient;
import site.omagotchi.learningservice.team.application.port.IdentityAccountSnapshot;
import site.omagotchi.learningservice.team.application.port.IdentityAccountState;
import site.omagotchi.learningservice.team.application.port.TeamMemberRepository;
import site.omagotchi.learningservice.team.support.TeamTestFixture;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willAnswer;

/** Identity HTTP 경계에서 Learning DB 트랜잭션이 끝났는지 확인하는 회귀 테스트. */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, TeamTestFixture.class})
class TeamIdentityTransactionBoundaryIT {

    @Autowired
    TeamTestFixture fixture;

    @Autowired
    TeamService teamService;

    @Autowired
    TeamMemberService teamMemberService;

    @Autowired
    TeamMemberRepository teamMemberRepository;

    @MockitoSpyBean
    IdentityAccountClient identityAccountClient;

    @Test
    @DisplayName("팀원 추가의 Identity 계정 상태 조회는 DB 트랜잭션 밖에서 실행된다")
    void accountStateLookupRunsWithoutDatabaseTransaction() {
        // Given: 추가 가능한 팀과 트랜잭션 비활성 상태를 검증하는 Identity 응답
        Long cohortId = fixture.createCohort("팀원 추가 트랜잭션 경계");
        TeamTestFixture.Member master = fixture.createActiveMember(cohortId);
        TeamTestFixture.Member target = fixture.createActiveMember(cohortId);
        Long teamId = teamService.create(cohortId, "추가 경계 팀", master.userId()).teamId();

        willAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new IdentityAccountSnapshot(IdentityAccountState.ACTIVE, Instant.EPOCH);
        }).given(identityAccountClient).getSnapshot(any());

        // When: 팀원 추가
        teamMemberService.addMember(teamId, target.userId(), master.userId());

        // Then: Identity 검증 뒤 쓰기 트랜잭션에서 팀원 저장
        assertThat(teamMemberRepository
                .findByTeamIdAndCohortMembershipId(teamId, target.membershipId()))
                .isPresent();
    }

    @Test
    @DisplayName("팀 상세의 Identity 표시 이름 조회는 DB 트랜잭션 밖에서 실행된다")
    void displayNameLookupRunsWithoutDatabaseTransaction() {
        // Given: 조회 가능한 팀과 트랜잭션 비활성 상태를 검증하는 Identity 응답
        Long cohortId = fixture.createCohort("팀 상세 트랜잭션 경계");
        TeamTestFixture.Member master = fixture.createActiveMember(cohortId);
        Long teamId = teamService.create(cohortId, "조회 경계 팀", master.userId()).teamId();

        willAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            Collection<UUID> userIds = invocation.getArgument(0);
            return userIds.stream()
                    .distinct()
                    .collect(Collectors.toUnmodifiableMap(userId -> userId, UUID::toString));
        }).given(identityAccountClient).findDisplayNames(any());

        // When: 팀 상세 조회
        var result = teamService.getTeam(teamId, master.userId());

        // Then: DB 조회 뒤 Identity 표시 이름을 붙인 결과 반환
        assertThat(result.members())
                .singleElement()
                .extracting(member -> member.displayName())
                .isEqualTo(master.userId().toString());
    }
}
