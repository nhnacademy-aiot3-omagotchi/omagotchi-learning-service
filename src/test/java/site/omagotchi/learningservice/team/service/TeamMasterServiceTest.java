package site.omagotchi.learningservice.team.service;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.ErrorCode;
import site.omagotchi.learningservice.team.application.TeamAccessSupport;
import site.omagotchi.learningservice.team.application.TeamErrorCode;
import site.omagotchi.learningservice.team.application.TeamMasterService;
import site.omagotchi.learningservice.team.application.TeamMembership;
import site.omagotchi.learningservice.team.application.port.TeamMemberRepository;
import site.omagotchi.learningservice.team.application.port.TeamRepository;
import site.omagotchi.learningservice.team.domain.Team;
import site.omagotchi.learningservice.team.domain.TeamMember;
import site.omagotchi.learningservice.team.domain.TeamMemberRole;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 마스터 위임·해체·자동 위임 (GR-12, GR-14, GR-16, GR-19, GR-20).
 *
 * <p>이 도메인의 불변식은 "팀당 MASTER 정확히 1명"인데 DB는 <b>최대 1명만</b> 보장한다 —
 * 부분 유니크는 0명을 막지 못한다. 그래서 순서와 커밋 전 검증을 여기서 고정한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class TeamMasterServiceTest {

    private static final Long TEAM_ID = 1L;
    private static final Long COHORT_ID = 1L;
    private static final Long MASTER_MEMBER_ID = 100L;
    private static final Long TARGET_MEMBER_ID = 200L;
    private static final Long MASTER_MEMBERSHIP_ID = 10L;
    private static final Long TARGET_MEMBERSHIP_ID = 20L;
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private TeamAccessSupport accessSupport;

    @InjectMocks
    private TeamMasterService teamMasterService;

    // ────────────────────────────── 위임 ──────────────────────────────

    @Test
    @DisplayName("위임하면 두 팀원의 역할이 교환된다.")
    void delegateSwapsRolesOfBothMembers() {
        TeamMember master = member(MASTER_MEMBER_ID, MASTER_MEMBERSHIP_ID, true);
        TeamMember target = member(TARGET_MEMBER_ID, TARGET_MEMBERSHIP_ID, false);
        givenDelegatable(master, target);

        teamMasterService.delegate(TEAM_ID, TARGET_MEMBER_ID, USER_ID);

        assertThat(master.isMaster()).isFalse();
        assertThat(target.isMaster()).isTrue();
    }

    /**
     * 역순이면 순간적으로 MASTER가 2명이 되어 {@code uq_team_members_one_master}를 위반한다.
     * 강등 저장이 먼저 flush돼야 승격이 인덱스를 통과한다.
     */
    @Test
    @DisplayName("강등을 먼저 저장한 뒤에 승격한다.")
    void savesDemotionBeforePromotion() {
        TeamMember master = member(MASTER_MEMBER_ID, MASTER_MEMBERSHIP_ID, true);
        TeamMember target = member(TARGET_MEMBER_ID, TARGET_MEMBERSHIP_ID, false);
        givenDelegatable(master, target);

        teamMasterService.delegate(TEAM_ID, TARGET_MEMBER_ID, USER_ID);

        InOrder order = inOrder(teamMemberRepository);
        order.verify(teamMemberRepository).save(master);
        order.verify(teamMemberRepository).save(target);
    }

    /** 정렬 없이 잠그면 두 위임 요청이 반대 순서로 행을 잡아 데드락이 난다. */
    @Test
    @DisplayName("팀 락을 잡은 뒤에 팀원 행을 잠근다.")
    void locksTeamBeforeLockingMemberRows() {
        TeamMember master = member(MASTER_MEMBER_ID, MASTER_MEMBERSHIP_ID, true);
        TeamMember target = member(TARGET_MEMBER_ID, TARGET_MEMBERSHIP_ID, false);
        givenDelegatable(master, target);

        teamMasterService.delegate(TEAM_ID, TARGET_MEMBER_ID, USER_ID);

        InOrder order = inOrder(accessSupport, teamMemberRepository);
        order.verify(accessSupport).lockActiveTeam(TEAM_ID);
        order.verify(teamMemberRepository).lockAllByTeamId(TEAM_ID);
    }

    @Test
    @DisplayName("자기 자신에게는 위임할 수 없다.")
    void cannotDelegateToSelf() {
        TeamMember master = member(MASTER_MEMBER_ID, MASTER_MEMBERSHIP_ID, true);
        givenLockedMembers(master, List.of(master));

        assertBusinessError(
                TeamErrorCode.CANNOT_DELEGATE_TO_SELF,
                () -> teamMasterService.delegate(TEAM_ID, MASTER_MEMBER_ID, USER_ID)
        );

        assertThat(master.isMaster()).isTrue();
    }

    /**
     * 대상 확인을 락 결과 안에서 하는 것이 요점이다. 락 밖에서 찾으면 그 사이 탈퇴한
     * 행을 승격시킬 수 있다 — 위임과 대상 탈퇴가 직렬화되지 않는다.
     */
    @Test
    @DisplayName("이 팀 소속이 아닌 대상에게는 위임할 수 없다.")
    void cannotDelegateToNonMember() {
        TeamMember master = member(MASTER_MEMBER_ID, MASTER_MEMBERSHIP_ID, true);
        givenLockedMembers(master, List.of(master));

        assertBusinessError(
                TeamErrorCode.MEMBER_NOT_FOUND,
                () -> teamMasterService.delegate(TEAM_ID, 999L, USER_ID)
        );

        assertThat(master.isMaster()).isTrue();
    }

    @Test
    @DisplayName("MASTER가 아니면 위임할 수 없다.")
    void cannotDelegateWhenRequesterIsNotMaster() {
        given(accessSupport.lockActiveTeam(TEAM_ID)).willReturn(team());
        given(accessSupport.requireActiveMembership(COHORT_ID, USER_ID))
                .willReturn(new TeamMembership(MASTER_MEMBERSHIP_ID, COHORT_ID, USER_ID));
        given(accessSupport.requireMaster(TEAM_ID, MASTER_MEMBERSHIP_ID))
                .willThrow(new BusinessException(TeamErrorCode.MASTER_REQUIRED));

        assertBusinessError(
                TeamErrorCode.MASTER_REQUIRED,
                () -> teamMasterService.delegate(TEAM_ID, TARGET_MEMBER_ID, USER_ID)
        );

        verify(teamMemberRepository, never()).lockAllByTeamId(TEAM_ID);
    }

    /**
     * 부분 유니크가 잡지 못하는 "0명"을 커밋 전에 잡는다. MASTER 없는 팀이 커밋되면
     * 아무도 그 팀을 해체하거나 관리할 수 없고 되살릴 API도 없다.
     */
    @Test
    @DisplayName("위임 후 MASTER가 정확히 1명이 아니면 거부한다.")
    void rejectsDelegationWhenMasterCountIsNotExactlyOne() {
        TeamMember master = member(MASTER_MEMBER_ID, MASTER_MEMBERSHIP_ID, true);
        TeamMember target = member(TARGET_MEMBER_ID, TARGET_MEMBERSHIP_ID, false);
        givenLockedMembers(master, List.of(master, target));
        given(teamMemberRepository.countByTeamIdAndRole(TEAM_ID, TeamMemberRole.MASTER))
                .willReturn(2L);

        assertBusinessError(
                TeamErrorCode.MASTER_STATE_CONFLICT,
                () -> teamMasterService.delegate(TEAM_ID, TARGET_MEMBER_ID, USER_ID)
        );
    }

    // ────────────────────────────── 해체 ──────────────────────────────

    /**
     * 팀원은 물리 삭제, 팀은 소프트 삭제다. 팀원을 남기면
     * {@code uq_team_members_membership}이 상태 조건 없는 유니크라 그 사람들이 어떤 팀에도
     * 다시 들어갈 수 없다.
     */
    @Test
    @DisplayName("해체하면 팀원은 지우고 팀은 소프트 삭제한다.")
    void disbandDeletesMembersAndSoftDeletesTeam() {
        Team team = team();
        given(accessSupport.lockActiveTeam(TEAM_ID)).willReturn(team);
        given(accessSupport.requireActiveMembership(COHORT_ID, USER_ID))
                .willReturn(new TeamMembership(MASTER_MEMBERSHIP_ID, COHORT_ID, USER_ID));

        teamMasterService.disband(TEAM_ID, USER_ID);

        verify(teamMemberRepository).deleteByTeamId(TEAM_ID);
        assertThat(team.isDisbanded()).isTrue();
    }

    @Test
    @DisplayName("MASTER가 아니면 해체할 수 없다.")
    void cannotDisbandWhenRequesterIsNotMaster() {
        Team team = team();
        given(accessSupport.lockActiveTeam(TEAM_ID)).willReturn(team);
        given(accessSupport.requireActiveMembership(COHORT_ID, USER_ID))
                .willReturn(new TeamMembership(MASTER_MEMBERSHIP_ID, COHORT_ID, USER_ID));
        given(accessSupport.requireMaster(TEAM_ID, MASTER_MEMBERSHIP_ID))
                .willThrow(new BusinessException(TeamErrorCode.MASTER_REQUIRED));

        assertBusinessError(
                TeamErrorCode.MASTER_REQUIRED,
                () -> teamMasterService.disband(TEAM_ID, USER_ID)
        );

        verify(teamMemberRepository, never()).deleteByTeamId(TEAM_ID);
        assertThat(team.isDisbanded()).isFalse();
    }

    // ────────────────────── 자동 위임 대상 선정 (GR-16) ──────────────────────

    /**
     * 회원 삭제 훅이 재시도될 수 있어 결정적이어야 한다. 정렬은 리포지토리가 하고
     * ({@code joined_at} 최소, 동률 시 {@code id} 최소) 서비스는 첫 원소를 고른다.
     */
    @Test
    @DisplayName("자동 위임 대상은 후보 목록의 첫 사람이다.")
    void autoSuccessorIsFirstCandidateInList() {
        TeamMember oldest = member(200L, 20L, false);
        TeamMember newer = member(300L, 30L, false);
        given(teamMemberRepository.findSuccessorCandidates(TEAM_ID, MASTER_MEMBER_ID))
                .willReturn(List.of(oldest, newer));

        assertThat(teamMasterService.findSuccessor(TEAM_ID, MASTER_MEMBER_ID)).contains(oldest);
    }

    /** 후보가 없다는 것은 "팀을 소프트 삭제해야 한다"는 뜻이다 — 예외가 아니다. */
    @Test
    @DisplayName("남은 팀원이 없으면 승계자가 없다.")
    void noSuccessorWhenNoMembersRemain() {
        given(teamMemberRepository.findSuccessorCandidates(TEAM_ID, MASTER_MEMBER_ID))
                .willReturn(List.of());

        assertThat(teamMasterService.findSuccessor(TEAM_ID, MASTER_MEMBER_ID)).isEmpty();
    }

    /** 떠나는 사람의 행이 아직 남아 있을 수 있어 후보에서 명시적으로 제외한다. */
    @Test
    @DisplayName("떠나는 사람을 제외하고 후보를 찾는다.")
    void excludesLeavingMemberFromSuccessorCandidates() {
        given(teamMemberRepository.findSuccessorCandidates(TEAM_ID, MASTER_MEMBER_ID))
                .willReturn(List.of());

        teamMasterService.findSuccessor(TEAM_ID, MASTER_MEMBER_ID);

        verify(teamMemberRepository).findSuccessorCandidates(TEAM_ID, MASTER_MEMBER_ID);
    }

    // ────────────────────────────── 헬퍼 ──────────────────────────────

    private void givenDelegatable(TeamMember master, TeamMember target) {
        givenLockedMembers(master, List.of(master, target));
        given(teamMemberRepository.countByTeamIdAndRole(TEAM_ID, TeamMemberRole.MASTER))
                .willReturn(1L);
    }

    private void givenLockedMembers(TeamMember master, List<TeamMember> locked) {
        given(accessSupport.lockActiveTeam(TEAM_ID)).willReturn(team());
        given(accessSupport.requireActiveMembership(COHORT_ID, USER_ID))
                .willReturn(new TeamMembership(MASTER_MEMBERSHIP_ID, COHORT_ID, USER_ID));
        given(accessSupport.requireMaster(TEAM_ID, MASTER_MEMBERSHIP_ID)).willReturn(master);
        given(teamMemberRepository.lockAllByTeamId(TEAM_ID)).willReturn(locked);
    }

    // ──────────────────── 소속 종료 정리 (GR-16) ────────────────────

    /**
     * 마스터의 소속이 끝나면 남은 팀원 중 하나가 뒤를 이어야 한다.
     * 여기서 승격하지 않으면 MASTER 0명인 팀이 커밋되고, 그 팀은 아무도 관리할 수 없다 —
     * 부분 유니크는 "최대 1명"만 보장해 이 상태를 막지 못한다.
     */
    @Test
    @DisplayName("마스터의 소속이 종료되면 남은 팀원에게 자동 위임한다")
    void promotesSuccessorWhenEndedMemberWasMaster() {
        TeamMember master = member(MASTER_MEMBER_ID, MASTER_MEMBERSHIP_ID, true);
        TeamMember successor = member(TARGET_MEMBER_ID, TARGET_MEMBERSHIP_ID, false);
        givenLockedTeamWith(master, successor);
        given(teamMemberRepository.findSuccessorCandidates(TEAM_ID, MASTER_MEMBER_ID))
                .willReturn(List.of(successor));
        given(teamMemberRepository.countByTeamIdAndRole(TEAM_ID, TeamMemberRole.MASTER))
                .willReturn(1L);

        assertThat(teamMasterService.removeEndedMember(MASTER_MEMBERSHIP_ID)).isTrue();

        verify(teamMemberRepository).delete(master);
        assertThat(successor.isMaster()).isTrue();
    }

    /** 마지막 한 명의 소속이 끝나면 승격시킬 대상이 없다 — 팀 자체가 사라져야 한다. */
    @Test
    @DisplayName("남은 팀원이 없으면 팀을 소프트 삭제한다")
    void disbandsTeamWhenNoSuccessorRemains() {
        TeamMember master = member(MASTER_MEMBER_ID, MASTER_MEMBERSHIP_ID, true);
        Team team = givenLockedTeamWith(master);
        given(teamMemberRepository.findSuccessorCandidates(TEAM_ID, MASTER_MEMBER_ID))
                .willReturn(List.of());

        assertThat(teamMasterService.removeEndedMember(MASTER_MEMBERSHIP_ID)).isTrue();

        verify(teamMemberRepository).delete(master);
        assertThat(team.isDisbanded()).isTrue();
    }

    /** 일반 팀원은 빠지기만 하면 된다 — 위임도 해체도 일어나지 않아야 한다. */
    @Test
    @DisplayName("일반 팀원의 소속이 종료되면 행만 삭제한다")
    void onlyDeletesRowWhenEndedMemberWasNotMaster() {
        TeamMember master = member(MASTER_MEMBER_ID, MASTER_MEMBERSHIP_ID, true);
        TeamMember leaving = member(TARGET_MEMBER_ID, TARGET_MEMBERSHIP_ID, false);
        Team team = givenLockedTeamWith(master, leaving);

        assertThat(teamMasterService.removeEndedMember(TARGET_MEMBERSHIP_ID)).isTrue();

        verify(teamMemberRepository).delete(leaving);
        verify(teamMemberRepository, never()).findSuccessorCandidates(any(), any());
        assertThat(team.isDisbanded()).isFalse();
        assertThat(master.isMaster()).isTrue();
    }

    /**
     * 훅은 재전달된다. 두 번째 실행이 예외를 던지면 재시도가 영원히 실패하고,
     * 그 뒤에 이어질 점유·알림 정리까지 함께 막힌다.
     */
    @Test
    @DisplayName("소속이 이미 팀에 없으면 아무것도 하지 않는다")
    void doesNothingWhenMembershipHasNoTeam() {
        given(teamMemberRepository.findTeamIdByCohortMembershipId(MASTER_MEMBERSHIP_ID))
                .willReturn(Optional.empty());

        assertThat(teamMasterService.removeEndedMember(MASTER_MEMBERSHIP_ID)).isFalse();

        verify(teamRepository, never()).findByIdForUpdate(any());
        verify(teamMemberRepository, never()).delete(any());
    }

    /**
     * 락 순서는 teams → team_members로 고정한다. 위임·해체와 같은 순서이며 어기면 데드락이다.
     * 특히 이 경로는 팀을 모른 채 시작하므로, 팀 식별자를 값으로 먼저 읽고 락을 잡는다.
     */
    @Test
    @DisplayName("팀 행을 먼저 잠근 뒤 팀원 행을 잠근다")
    void locksTeamBeforeMembersOnCleanup() {
        TeamMember master = member(MASTER_MEMBER_ID, MASTER_MEMBERSHIP_ID, true);
        givenLockedTeamWith(master);
        given(teamMemberRepository.findSuccessorCandidates(TEAM_ID, MASTER_MEMBER_ID))
                .willReturn(List.of());

        teamMasterService.removeEndedMember(MASTER_MEMBERSHIP_ID);

        InOrder order = inOrder(teamRepository, teamMemberRepository);
        order.verify(teamRepository).findByIdForUpdate(TEAM_ID);
        order.verify(teamMemberRepository).lockAllByTeamId(TEAM_ID);
    }

    private Team givenLockedTeamWith(TeamMember... members) {
        Team team = team();
        given(teamMemberRepository.findTeamIdByCohortMembershipId(anyLong()))
                .willReturn(Optional.of(TEAM_ID));
        given(teamRepository.findByIdForUpdate(TEAM_ID)).willReturn(Optional.of(team));
        given(teamMemberRepository.lockAllByTeamId(TEAM_ID)).willReturn(List.of(members));
        return team;
    }

    private static Team team() {
        Team team = Team.create(COHORT_ID, "테스트");
        ReflectionTestUtils.setField(team, "id", TEAM_ID);
        return team;
    }

    private static TeamMember member(Long id, Long membershipId, boolean master) {
        TeamMember member = master
                ? TeamMember.master(TEAM_ID, membershipId)
                : TeamMember.member(TEAM_ID, membershipId);
        ReflectionTestUtils.setField(member, "id", id);
        ReflectionTestUtils.setField(member, "joinedAt", OffsetDateTime.now());
        return member;
    }

    private void assertBusinessError(ErrorCode expectedErrorCode, ThrowingCallable action) {
        assertThatThrownBy(action)
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode()
                ).isEqualTo(expectedErrorCode));
    }
}
