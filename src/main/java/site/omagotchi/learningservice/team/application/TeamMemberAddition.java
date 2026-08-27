package site.omagotchi.learningservice.team.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.cohort.application.result.CohortMembershipView;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.team.application.port.TeamMemberRepository;
import site.omagotchi.learningservice.team.domain.Team;
import site.omagotchi.learningservice.team.domain.TeamMember;

import java.util.UUID;

/**
 * Identity 계정 검증이 끝난 팀원 추가를 DB에 반영하는 내부 트랜잭션 역할.
 *
 * <p>Identity 조회 전 접근 제어를 통과했어도 응답을 기다리는 동안 팀 해체·MASTER 위임·동시
 * 팀원 추가가 커밋될 수 있다. 따라서 이 Component는 접근 제어 결과를 입력으로 받지 않고,
 * 팀 행 락을 먼저 잡은 뒤 저장에 필요한 상태와 권한을 전부 다시 읽는다.</p>
 *
 * <p>락 순서는 팀 도메인의 공통 규칙인 {@code teams → team_members}다. 이 Method가
 * 반환될 때까지 같은 팀의 위임·해체·팀원 변경이 직렬화되므로, 락 이후 확인한 MASTER
 * 권한과 정원 카운트가 INSERT까지 유효하다.</p>
 */
@Component
@RequiredArgsConstructor
public class TeamMemberAddition {

    private final TeamMemberRepository teamMemberRepository;
    private final TeamAccessSupport accessSupport;
    private final CohortMembershipQueryService cohortMembershipQueryService;

    /**
     * 팀 상태·권한·대상 소속·정원을 락 안에서 확인하고 팀원을 추가한다.
     */
    @Transactional
    public void add(Long teamId, UUID targetUserId, UUID userId) {
        Team lockedTeam = accessSupport.lockActiveTeam(teamId);

        TeamMembership requestMembership = accessSupport.requireActiveMembership(
                lockedTeam.getCohortId(), userId);
        accessSupport.requireMaster(teamId, requestMembership.id());

        // GR-22: 팀의 기수로 대상을 역조회한다. Identity 계정 조회 중 소속이 끝날 수 있으므로
        // 락을 잡은 이 쓰기 트랜잭션에서 현재 활성 멤버십을 읽는다.
        CohortMembershipView targetMembership = cohortMembershipQueryService
                .findActiveMembership(lockedTeam.getCohortId(), targetUserId)
                .orElseThrow(() -> new BusinessException(TeamErrorCode.TARGET_NOT_IN_COHORT));

        // 정원은 "최대 8행"이라 DB 유니크 제약으로 표현할 수 없다. 같은 teams 행을
        // 잡은 상태에서 세고 INSERT해야 동시 추가가 8명을 넘지 않는다.
        if (teamMemberRepository.countByTeamId(teamId) >= TeamMember.MAX_MEMBERS_PER_TEAM) {
            throw new BusinessException(TeamErrorCode.CAPACITY_EXCEEDED);
        }

        // 다른 팀 동시 가입은 uq_team_members_membership이 최종 직렬화하고,
        // Persistence Adapter가 ALREADY_IN_TEAM으로 변환한다.
        teamMemberRepository.save(TeamMember.member(teamId, targetMembership.membershipId()));
    }
}
