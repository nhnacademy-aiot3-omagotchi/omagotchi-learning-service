package site.omagotchi.learningservice.team.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.team.application.TeamAccessSupport;
import site.omagotchi.learningservice.team.application.TeamDetailLookup;
import site.omagotchi.learningservice.team.application.TeamErrorCode;
import site.omagotchi.learningservice.team.application.TeamMembership;
import site.omagotchi.learningservice.team.application.port.TeamMemberRepository;
import site.omagotchi.learningservice.team.application.result.TeamDetailLocalResult;
import site.omagotchi.learningservice.team.domain.Team;
import site.omagotchi.learningservice.team.domain.TeamMember;
import site.omagotchi.learningservice.team.domain.TeamMemberRole;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TeamDetailLookupTest {

    @Mock
    TeamMemberRepository teamMemberRepository;

    @Mock
    TeamAccessSupport accessSupport;

    @Mock
    CohortMembershipQueryService cohortMembershipQueryService;

    @InjectMocks
    TeamDetailLookup teamDetailLookup;

    private final UUID requesterUserId = UUID.randomUUID();

    @Test
    @DisplayName("팀 상세 DB 값을 마스터 우선 순서의 로컬 결과로 복사한다")
    void copiesTeamDetailToLocalResult() {
        // Given: 조회 권한이 있는 요청자와 가입 순서가 섞인 팀원 목록
        Long teamId = 100L;
        Team team = teamWithId(teamId, 10L);
        TeamMembership requesterMembership =
                new TeamMembership(1L, 10L, requesterUserId);
        UUID masterUserId = UUID.randomUUID();
        UUID memberUserId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        TeamMember master = memberWithId(
                1L, teamId, 1L, TeamMemberRole.MASTER, now.plusMinutes(5));
        TeamMember member = memberWithId(
                2L, teamId, 2L, TeamMemberRole.MEMBER, now);

        given(accessSupport.loadActiveTeam(teamId)).willReturn(team);
        given(accessSupport.requireActiveMembership(10L, requesterUserId))
                .willReturn(requesterMembership);
        given(accessSupport.requireMembership(teamId, requesterMembership.id()))
                .willReturn(master);
        given(teamMemberRepository.findByTeamIdOrderByJoinedAtAsc(teamId))
                .willReturn(List.of(member, master));
        given(cohortMembershipQueryService.findUserIds(List.of(2L, 1L)))
                .willReturn(Map.of(1L, masterUserId, 2L, memberUserId));

        // When: 팀 상세에 필요한 Learning DB 값 조회
        TeamDetailLocalResult localResult = teamDetailLookup.load(teamId, requesterUserId);

        // Then: 접근 권한을 확인하고 마스터 우선 순서의 로컬 결과 반환
        verify(accessSupport).requireMembership(teamId, 1L);
        assertThat(localResult.myMemberId()).isEqualTo(1L);
        assertThat(localResult.myRole()).isEqualTo(TeamMemberRole.MASTER);
        assertThat(localResult.members())
                .extracting(
                        TeamDetailLocalResult.Member::memberId,
                        TeamDetailLocalResult.Member::userId,
                        TeamDetailLocalResult.Member::role
                )
                .containsExactly(
                        tuple(1L, masterUserId, TeamMemberRole.MASTER),
                        tuple(2L, memberUserId, TeamMemberRole.MEMBER)
                );
    }

    @Test
    @DisplayName("팀 접근 권한이 없으면 팀원 로컬 결과를 조회하지 않는다")
    void rejectsRequesterWithoutTeamAccess() {
        // Given: 팀의 기수에는 속하지만 해당 팀에는 소속되지 않은 요청자
        Long teamId = 100L;
        Team team = teamWithId(teamId, 10L);
        TeamMembership requesterMembership =
                new TeamMembership(1L, 10L, requesterUserId);

        given(accessSupport.loadActiveTeam(teamId)).willReturn(team);
        given(accessSupport.requireActiveMembership(10L, requesterUserId))
                .willReturn(requesterMembership);
        given(accessSupport.requireMembership(teamId, requesterMembership.id()))
                .willThrow(new BusinessException(TeamErrorCode.NOT_A_MEMBER));

        // When & Then: 접근 권한 오류 반환
        assertThatThrownBy(() -> teamDetailLookup.load(teamId, requesterUserId))
                .hasFieldOrPropertyWithValue("errorCode", TeamErrorCode.NOT_A_MEMBER);

        // Then: 팀원 목록 조회 시도 없음
        verify(teamMemberRepository, never()).findByTeamIdOrderByJoinedAtAsc(any());
    }

    private static Team teamWithId(Long teamId, Long cohortId) {
        Team team = Team.create(cohortId, "테스트");
        ReflectionTestUtils.setField(team, "id", teamId);
        return team;
    }

    private static TeamMember memberWithId(
            Long memberId,
            Long teamId,
            Long membershipId,
            TeamMemberRole role,
            OffsetDateTime joinedAt
    ) {
        TeamMember member = role == TeamMemberRole.MASTER
                ? TeamMember.master(teamId, membershipId)
                : TeamMember.member(teamId, membershipId);
        ReflectionTestUtils.setField(member, "id", memberId);
        ReflectionTestUtils.setField(member, "joinedAt", joinedAt);
        return member;
    }
}
