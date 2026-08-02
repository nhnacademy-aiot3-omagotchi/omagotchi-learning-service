package site.omagotchi.learningservice.team.application.port;

import site.omagotchi.learningservice.team.domain.TeamMember;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * {@code team_members} Persistence 경계.
 *
 * <p>이 테이블에는 상태 컬럼이 없다. 행의 존재 자체가 소속이고, 탈퇴·제외·해체는 모두
 * 물리 삭제다. 따라서 여기에 "활성 필터"는 없으며, 소프트 삭제 Method를 추가하면
 * 옛 행이 {@code uq_team_members_membership}을 점유해 재가입이 영구히 막힌다.</p>
 *
 * <p>주체 키가 {@code cohort_membership_id}인 것도 유의한다. 같은 사람이라도 기수가
 * 다르면 다른 값이므로, "이 계정이 팀에 있나"를 계정 id로 묻는 Method는 여기 없다.</p>
 *
 * <p>위임·해체·자동 위임(이슈 #8)에 필요한 조회는 아직 이 Port에 없다. 구현체 쪽
 * {@code TeamMemberJpaRepository}에 준비돼 있으니, 해당 서비스를 쓸 때 여기로 올린다 —
 * 쓰지 않는 Method를 미리 Port에 두지 않는다.</p>
 */
public interface TeamMemberRepository {

    /**
     * 팀원 행을 저장하고 즉시 flush한다.
     *
     * <p>flush를 지연하지 않는 것이 계약의 일부다. 커밋 시점까지 밀면 유니크 위반이
     * 트랜잭션 경계 밖에서 터져 {@code ErrorCode}로 변환되지 못하고 500이 된다.</p>
     *
     * @throws site.omagotchi.learningservice.global.exception.BusinessException
     *         {@code uq_team_members_membership}·{@code uq_team_members_team_membership} 위반 시
     *         {@code TEAM_ALREADY_IN_TEAM}(409), {@code uq_team_members_one_master} 위반 시
     *         {@code TEAM_MASTER_STATE_CONFLICT}(409)
     */
    TeamMember save(TeamMember member);

    /** 팀원 행 물리 삭제. 탈퇴·제외 공통이다. */
    void delete(TeamMember member);

    /**
     * {@code team_members.id}로 단건 조회.
     *
     * <p>이 값은 팀을 가리지 않으므로, 다른 팀의 id가 넘어올 수 있다.
     * 호출부가 {@code teamId} 일치를 반드시 재확인해야 한다.</p>
     */
    Optional<TeamMember> findById(Long id);

    /**
     * 이 멤버십이 이미 어떤 팀에든 소속됐는지 (GR-18).
     *
     * <p>팀 id를 받지 않는 것이 핵심이다. 멤버십은 기수당 1행이므로 팀을 가리지 않는
     * 이 검사가 곧 "기수당 1인 1팀"이 된다. 다른 기수의 팀에 소속된 것은 다른 멤버십이라
     * 여기 걸리지 않으며, 그게 정상이다.</p>
     */
    boolean existsByCohortMembershipId(Long cohortMembershipId);

    /**
     * 여러 멤버십의 소속을 한 번에 조회한다 (GR-06).
     *
     * <p>다기수 담당자는 기수별로 팀이 하나씩 있을 수 있어 내 팀 목록이 복수 건이 된다.
     * 멤버십마다 따로 조회하지 않도록 배치로 받는다.</p>
     */
    List<TeamMember> findByCohortMembershipIdIn(Collection<Long> cohortMembershipIds);

    /** 특정 팀에서의 소속 행. 권한 검증(팀원인가·MASTER인가)의 진입점이다. */
    Optional<TeamMember> findByTeamIdAndCohortMembershipId(Long teamId, Long cohortMembershipId);

    /**
     * 팀 현재 인원 (GR-17).
     *
     * <p>반드시 {@code teams} 행 락을 잡은 트랜잭션 안에서 호출해야 한다.
     * 락 밖에서 세면 7명 팀에 두 요청이 동시에 들어와 9명이 된다 —
     * "최대 8행"은 유니크 인덱스로 표현할 수 없어 DB가 잡아주지 못한다.</p>
     */
    long countByTeamId(Long teamId);

    /**
     * 팀원 목록 (GR-15). 가입 순으로 반환한다.
     *
     * <p>표시 순서(마스터 우선)는 Application이 다시 정렬한다. 여기서 joined_at 순인 것은
     * 자동 위임 기준(GR-16: joined_at 최소)과 같은 축을 쓰기 위해서다.</p>
     */
    List<TeamMember> findByTeamIdOrderByJoinedAtAsc(Long teamId);
}
