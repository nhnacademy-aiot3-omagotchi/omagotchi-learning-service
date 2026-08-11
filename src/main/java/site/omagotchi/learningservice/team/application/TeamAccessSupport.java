package site.omagotchi.learningservice.team.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.cohort.application.result.CohortMembershipView;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.team.domain.Team;
import site.omagotchi.learningservice.team.domain.TeamMember;
import site.omagotchi.learningservice.team.domain.TeamMemberRole;
import site.omagotchi.learningservice.team.application.port.TeamMemberRepository;
import site.omagotchi.learningservice.team.application.port.TeamRepository;

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
    private final CohortMembershipQueryService cohortMembershipQueryService;

    /**
     * 팀 생성 요청자의 대상 기수를 확정한다 (RM-28).
     *
     * cohortId를 클라이언트가 보내지만 신뢰하지 않는다 — 요청자의 것인지 서버가 검증한다.
     * 역할은 제한하지 않는다: 매니저·멘토도 담당 기수의 팀에 소속될 수 있다(명세 05 v3).
     */
    public TeamMembership resolveMembershipForCreate(Long cohortId, UUID userId) {
        if (cohortId != null) {
            return requireActiveMembership(cohortId, userId);
        }

        // 학생은 활성 맴버십 1개지만 매니저, 멘토는 여러 기수를 동시에 담당할 수 있어
        // 서버가 "요청자의 기수"를 단정할 수 없다. 이때만 클라이언트에 지정을 요구한다.
        List<CohortMembershipView> memberships =
                cohortMembershipQueryService.findActiveMemberships(userId);
        if (memberships.isEmpty()) {
            throw new BusinessException(TeamErrorCode.COHORT_ACCESS_DENIED);
        }
        if (memberships.size() > 1) {
            throw new BusinessException(TeamErrorCode.COHORT_REQUIRED);
        }
        return toTeamMembership(memberships.getFirst());
    }

    /**
     * 요청자가 해당 기수의 활성 멤버십을 가지는지 확인한다.
     * 팀 조작 권한은 모두 팀의 기수 기준으로 판정한다 —
     * 기수가 종료돼 멤버십이 ENDED가 되면 MASTER였어도 여기서 막힌다.
     */
    public TeamMembership requireActiveMembership(Long cohortId, UUID userId) {
        return cohortMembershipQueryService.findActiveMembership(cohortId, userId)
                .map(TeamAccessSupport::toTeamMembership)
                .orElseThrow(() -> new BusinessException(TeamErrorCode.COHORT_ACCESS_DENIED));
    }

    /**
     * 기수 파트의 공개 계약을 팀의 표현으로 옮긴다.
     *
     * <p>필드가 같아 그대로 써도 되지만 경계에서 한 번 변환한다. {@code TeamMembership}은
     * "팀이 아는 멤버십"이라는 의미를 갖고 있고(status가 없는 이유가 그 javadoc에 있다),
     * 기수 파트가 {@code CohortMembershipView}에 필드를 늘려도 팀 코드가 흔들리지 않는다.</p>
     */
    private static TeamMembership toTeamMembership(CohortMembershipView view) {
        return new TeamMembership(view.membershipId(), view.cohortId(), view.userId());
    }

    /**
     * 해체되지 않은 팀을 엔티티로 읽는다. 락은 잡지 않는다.
     *
     * <p>조회 전용 경로에서만 쓴다. 이후 같은 트랜잭션에서
     * {@link #lockActiveTeam(Long)}을 부를 계획이라면 이 메서드를 쓰면 안 된다 —
     * 여기서 올라간 1차 캐시 인스턴스가 락 이후 재확인을 무력화한다.
     * 그 경우엔 {@link #requireActiveTeamCohortId(Long)}를 쓴다.</p>
     *
     * @throws site.omagotchi.learningservice.global.exception.BusinessException 없거나 해체된 팀이면 404
     */
    public Team loadActiveTeam(Long teamId) {
        return teamRepository.findByIdAndDeletedAtIsNull(teamId)
                .orElseThrow(() -> new BusinessException(TeamErrorCode.TEAM_NOT_FOUND));
    }

    /**
     * 락을 잡기 전에 팀의 기수만 확인한다.
     *
     * 팀원 추가처럼 "검증은 락 밖, 카운트·INSERT만 락 안"인 흐름에서 쓴다.
     * 검증에 필요한 건 cohort_id 하나뿐인데 loadActiveTeam으로 엔티티를 읽으면
     * 그 인스턴스가 1차 캐시에 남아 뒤따르는 lockActiveTeam의 deleted_at 재확인을
     * 락 이전 스냅샷으로 만들어버린다. 락 전에는 Team을 엔티티로 만들지 않는다.
     */
    public Long requireActiveTeamCohortId(Long teamId) {
        return teamRepository.findActiveCohortId(teamId)
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

    /**
     * 해당 멤버십이 이 팀의 팀원인지 확인하고 그 행을 돌려준다.
     *
     * <p>실패가 404가 아니라 403인 것이 의도다. "그 팀에 당신 행이 없다"는 사실 자체가
     * 남의 팀 구성에 대한 정보이므로, 존재 여부를 알려주지 않고 권한 없음으로 끊는다.</p>
     *
     * @param cohortMembershipId 계정 id가 아니라 멤버십 id다. 같은 사람이라도 기수가 다르면 다른 값이다
     * @throws site.omagotchi.learningservice.global.exception.BusinessException 팀원이 아니면 403
     */
    public TeamMember requireMembership(Long teamId, Long cohortMembershipId) {
        return teamMemberRepository.findByTeamIdAndCohortMembershipId(teamId, cohortMembershipId)
                .orElseThrow(() -> new BusinessException(TeamErrorCode.NOT_A_MEMBER));
    }

    /**
     * 팀 관리 권한 검증. 팀원 추가·제외·위임·해체의 공통 관문이다.
     *
     * <p>반드시 {@code teams} 행 락을 잡은 뒤에 호출해야 의미가 있다. 락 밖에서 통과시키면
     * 그 사이 위임이 커밋되어 이미 MEMBER가 된 사람이 관리 작업을 이어갈 수 있다.</p>
     *
     * @throws site.omagotchi.learningservice.global.exception.BusinessException 팀원이 아니거나 MASTER가 아니면 403
     */
    public TeamMember requireMaster(Long teamId, Long cohortMembershipId) {
        TeamMember member = requireMembership(teamId, cohortMembershipId);
        if (!member.isMaster()) {
            throw new BusinessException(TeamErrorCode.MASTER_REQUIRED);
        }
        return member;
    }

    /**
     * {@code teams} 행 락을 잡은 뒤 MASTER 권한을 값으로 다시 확인한다.
     *
     * <p>{@link #requireMaster}를 그대로 재호출하면 안 된다. 같은 트랜잭션에서 락 밖 사전
     * 검증이 이미 {@link #requireMaster}(또는 {@link #requireMembership})를 호출해
     * 그 멤버십의 {@code TeamMember}를 엔티티로 읽었다면, 영속성 컨텍스트에 캐시된 그
     * 인스턴스가 재조회의 반환값이 된다 — 그 사이 다른 트랜잭션이 위임을 커밋해 role이
     * 바뀌었어도 캐시는 락 이전 스냅샷을 그대로 들고 있다. 이 메서드는 엔티티가 아니라
     * boolean 값으로 확인해 이 함정을 피한다.</p>
     *
     * @throws site.omagotchi.learningservice.global.exception.BusinessException MASTER가 아니면 403
     */
    public void requireStillMaster(Long teamId, Long cohortMembershipId) {
        boolean master = teamMemberRepository.existsByTeamIdAndCohortMembershipIdAndRole(
                teamId, cohortMembershipId, TeamMemberRole.MASTER);
        if (!master) {
            throw new BusinessException(TeamErrorCode.MASTER_REQUIRED);
        }
    }
}
