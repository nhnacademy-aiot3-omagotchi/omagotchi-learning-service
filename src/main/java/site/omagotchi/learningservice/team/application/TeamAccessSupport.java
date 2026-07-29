package site.omagotchi.learningservice.team.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.team.domain.Team;
import site.omagotchi.learningservice.team.domain.TeamErrorCode;
import site.omagotchi.learningservice.team.domain.TeamMember;
import site.omagotchi.learningservice.team.infrastructure.TeamMemberRepository;
import site.omagotchi.learningservice.team.infrastructure.TeamRepository;

import java.util.List;
import java.util.UUID;

/**
 * 세 서비스가 공유하는 조회·검증 헬퍼.
 * 락을 잡는 코드를 한곳에 모아 순서를 눈으로 확인할 수 있게 한다 — 항상 teams 먼저.
 */
@Component
@RequiredArgsConstructor
public class TeamAccessSupport {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final MembershipReader membershipReader;

    /**
     * 팀 생성 요청자의 대상 기수를 확정한다 (RM-28).
     *
     * cohortId를 클라이언트가 보내지만 신뢰하지 않는다 — 요청자의 것인지 서버가 검증한다.
     * 역할은 제한하지 않는다: 매니저·멘토도 담당 기수의 팀에 소속될 수 있다(명세 05 v3).
     */
    public TeamMembership resolveMembershipForCreate(Long cohortId, UUID userId) {
        if (cohortId != null) {
            return membershipReader.findActive(cohortId, userId)
                    .orElseThrow(() -> new BusinessException(TeamErrorCode.COHORT_ACCESS_DENIED));
        }

        // 학생은 활성 맴버십 1개지만 매니저, 멘토는 여러 기수를 동시에 담당할 수 있어
        // 서버가 "요청자의 기수"를 단정할 수 없다. 이때만 클라이언트에 지정을 요구한다.
        List<TeamMembership> memberships = membershipReader.findActiveAll(userId);
        if (memberships.isEmpty()) {
            throw new BusinessException(TeamErrorCode.COHORT_ACCESS_DENIED);
        }
        if (memberships.size() > 1) {
            throw new BusinessException(TeamErrorCode.COHORT_REQUIRED);
        }
        return memberships.getFirst();
    }

    /**
     * 요청자가 해당 기수의 활성 멤버십을 가지는지 확인한다.
     * 팀 조작 권한은 모두 팀의 기수 기준으로 판정한다 —
     * 기수가 종료돼 멤버십이 ENDED가 되면 MASTER였어도 여기서 막힌다.
     */
    public TeamMembership requiredActiveMembership(Long cohortId, UUID userId) {
        return membershipReader.findActive(cohortId, userId)
                .orElseThrow(() -> new BusinessException(TeamErrorCode.COHORT_ACCESS_DENIED));
    }

    public Team loadActiveTeam(Long teamId) {
        return teamRepository.findByIdAndDeletedAtIsNull(teamId)
                .orElseThrow(() -> new BusinessException(TeamErrorCode.TEAM_NOT_FOUND));
    }

    /**
     * 팀 행을 배타 락으로 잡고 해체 여부를 재확인한다.
     *
     * 락 획득 "후" deleted_at을 보는 것이 핵심이다. 쿼리 조건에 넣으면
     * 해체 커밋 직후 도착한 요청이 그냥 "행 없음"으로 빠지지만,
     * 이렇게 하면 해체가 끝난 시점을 정확히 보고 404를 준다.
     */
    public Team lockActiveTeam(Long teamId) {
        Team team = teamRepository.findByIdForUpdate(teamId)
                .orElseThrow(() -> new BusinessException(TeamErrorCode.TEAM_NOT_FOUND));
        if (team.isDisbanded()) {
            throw new BusinessException(TeamErrorCode.TEAM_NOT_FOUND);
        }
        return team;
    }

    public TeamMember requireMembership(Long teamId, Long cohortMembershipId) {
        return teamMemberRepository.findByTeamIdAndCohortMembershipId(teamId, cohortMembershipId)
                .orElseThrow(() -> new BusinessException(TeamErrorCode.NOT_A_MEMBER));
    }

    public TeamMember requireMaster(Long teamId, Long cohortMembershipId) {
        TeamMember member = requireMembership(teamId, cohortMembershipId);
        if (!member.isMaster()) {
            throw new BusinessException(TeamErrorCode.MASTER_REQUIRED);
        }
        return member;
    }
}
