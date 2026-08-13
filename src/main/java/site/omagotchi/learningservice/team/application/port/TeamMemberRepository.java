package site.omagotchi.learningservice.team.application.port;

import site.omagotchi.learningservice.team.domain.TeamMember;
import site.omagotchi.learningservice.team.domain.TeamMemberRole;

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
 * <p>아래쪽 네 Method(락·자동 위임 후보·일괄 삭제·역할 카운트)는 위임·해체(이슈 #8)가
 * 쓴다. 앞쪽과 달리 <b>전부 트랜잭션 안에서만 의미가 있다</b> — 락 없이 부르거나 순서를
 * 바꾸면 "MASTER 0명 또는 2명"이 커밋될 수 있다.</p>
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

    /**
     * 팀원 행 물리 삭제. 탈퇴·제외·소속 종료 정리 공통이다.
     *
     * <p><b>DELETE가 이 Method 안에서 실행된다.</b> {@link #save}가 {@code saveAndFlush}인 것과
     * 같은 이유로 flush 시점을 여기서 고정한다 — 미루면 Hibernate가 flush를 커밋 시점까지
     * 모아두고, 그때 <b>DELETE를 UPDATE보다 나중에</b> 실행한다(insert → update → … → delete).</p>
     *
     * <p>그 순서가 MASTER 교체를 깨뜨린다. {@code TeamMasterService.removeEndedMember}는
     * "기존 MASTER 행 삭제 → 후임 승격" 순으로 진행하는데, 삭제가 승격 UPDATE 뒤로 밀리면
     * 순간적으로 MASTER가 2행이 되어 {@code uq_team_members_one_master}를 위반한다.
     * 여기서 flush하면 호출 순서가 곧 SQL 실행 순서가 된다.</p>
     */
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
     * 이 멤버십이 속한 팀을 스칼라로 읽는다 (GR-16).
     *
     * <p>소속 종료 정리는 팀을 모른 채 시작하므로 먼저 찾아야 하는데, <b>엔티티가 아니라 값
     * 하나만 뽑는 것이 이 Method의 존재 이유다.</b> {@code TeamMember}를 먼저 읽으면 그
     * 인스턴스가 1차 캐시에 올라가고, 뒤이은 {@link #lockAllByTeamId}가
     * {@code FOR UPDATE}를 실제로 실행해도 Hibernate는 캐시의 인스턴스를 돌려준다 —
     * 그러면 락 이후 role 재확인이 락 이전 스냅샷을 보게 되어, 그 사이 커밋된 위임을
     * 놓친다 ({@code TeamRepository.findActiveCohortId}와 같은 함정).</p>
     *
     * <p>팀 필터가 없어도 되는 것은 멤버십이 기수당 1행이고
     * {@code uq_team_members_membership}이 한 멤버십의 소속을 하나로 강제하기 때문이다.</p>
     */
    Optional<Long> findTeamIdByCohortMembershipId(Long cohortMembershipId);

    /**
     * 이 멤버십이 이 팀의 MASTER인가. 값(boolean)으로만 확인한다.
     *
     * <p>같은 트랜잭션에서 {@link #findByTeamIdAndCohortMembershipId}로 이미 이 멤버십의
     * {@code TeamMember}를 엔티티로 읽었다면, 그 인스턴스가 영속성 컨텍스트에 캐시되어
     * 같은 조회를 다시 해도 그대로 반환된다 — 그 사이 다른 트랜잭션이 커밋한 위임(role
     * 변경)을 보지 못한다. {@code teams} 행 락을 잡은 뒤 MASTER 권한을 재확인할 때는
     * 반드시 이 메서드를 쓴다({@code TeamAccessSupport.requireStillMaster} 참고).</p>
     */
    boolean existsByTeamIdAndCohortMembershipIdAndRole(
            Long teamId, Long cohortMembershipId, TeamMemberRole role);

    /**
     * 팀 현재 인원 (GR-17).
     *
     * <p>반드시 {@code teams} 행 락을 잡은 트랜잭션 안에서 호출해야 한다.
     * 락 밖에서 세면 7명 팀에 두 요청이 동시에 들어와 9명이 된다 —
     * "최대 8행"은 유니크 인덱스로 표현할 수 없어 DB가 잡아주지 못한다.</p>
     */
    long countByTeamId(Long teamId);


    /**
     * 정합성 스윕이 순회할 소속 행을 {@code id} 오름차순 배치로 읽는다 (ADR 0013).
     *
     * <p><b>이 테이블만 읽는 것이 요점이다.</b> "소속이 아직 살아 있나"는
     * {@code CohortMembershipQueryService#findInactiveMembershipIds}에 묻는다 — 여기서
     * {@code cohort_memberships}를 조인하면 팀의 infrastructure가 기수 파트 테이블을 알게 되고,
     * 그것이 지금 다른 곳에서 걷어내고 있는 위반과 같은 종류다.</p>
     *
     * <p><b>드라이빙 테이블이 이쪽인 것도 의도다.</b> {@code team_members}는 물리 삭제라
     * 행 수가 현재 소속 수로 유계이지만, 종료된 소속은 기수가 끝날 때마다 영구히 누적된다.
     * 종료 소속에서 출발하면 스캔 대상이 해마다 늘고 새 인덱스가 필요해진다.</p>
     *
     * <p>엔티티가 아니라 값을 돌려주는 이유는 1차 캐시다. {@link TeamMember}로 읽으면 그
     * 인스턴스가 영속성 컨텍스트에 올라가고, 뒤이어 정리가 {@link #lockAllByTeamId}로
     * {@code FOR UPDATE}를 걸어도 Hibernate가 캐시 인스턴스를 그대로 돌려준다.</p>
     *
     * @param afterId 이 값보다 큰 {@code team_members.id}부터. 첫 배치는 0
     * @param limit   한 배치 크기
     * @return {@code id} 오름차순. 비면 순회 종료다
     */
    List<MembershipRef> findMembershipRefsAfter(Long afterId, int limit);


    /**
     * 스윕이 한 행에 대해 알아야 하는 최소 정보.
     *
     * @param teamMemberId       커서 전진용. 정리 대상 지정에는 쓰지 않는다
     * @param cohortMembershipId 소속 유효성 판정과 정리 진입점의 인자
     */
    record MembershipRef(Long teamMemberId, Long cohortMembershipId) {
    }

    /**
     * 팀원 목록 (GR-15). 가입 순으로 반환한다.
     *
     * <p>표시 순서(마스터 우선)는 Application이 다시 정렬한다. 여기서 joined_at 순인 것은
     * 자동 위임 기준(GR-16: joined_at 최소)과 같은 축을 쓰기 위해서다.</p>
     */
    List<TeamMember> findByTeamIdOrderByJoinedAtAsc(Long teamId);


    /**
     * 팀원 행 전체를 배타 락으로 잡는다 (GR-20). 반드시 트랜잭션 안에서 호출한다.
     *
     * <p><b>id 오름차순으로 잠근다.</b> 정렬하지 않으면 두 위임 요청이 반대 순서로 행을
     * 잠가 데드락이 난다 — 위임은 두 행(강등 대상·승격 대상)을 함께 만지므로 순서가
     * 엇갈릴 여지가 있다.</p>
     *
     * <p>{@code teams} 행 락을 먼저 잡은 뒤에 호출해야 한다. 락 순서
     * {@code teams} → {@code team_members}는 팀 도메인 전체가 공유하는 규칙이다.</p>
     */
    List<TeamMember> lockAllByTeamId(Long teamId);


    /**
     * 자동 위임 후보를 순서대로 조회한다 (GR-16).
     *
     * <p>정렬 기준은 {@code joined_at} 최소, 동률 시 {@code id} 최소다. 결정적이어야 하는
     * 이유는 회원 삭제 훅이 재시도될 수 있기 때문이다 — {@code joined_at}만으로 정렬하면
     * 같은 시각에 추가된 팀원 사이에서 순서가 매번 달라져, 두 번째 실행이 첫 번째와 다른
     * 사람을 MASTER로 만든다.</p>
     *
     * <p>단건이 아니라 목록인 것은 "후보가 없음"(= 팀을 소프트 삭제해야 함)을 호출부가
     * 빈 리스트로 구분하게 하려는 것이다.</p>
     *
     * @param excludedMemberId 떠나는 사람의 {@code team_members.id}. 아직 행이 남아 있을 수 있어 명시적으로 제외한다
     */
    List<TeamMember> findSuccessorCandidates(Long teamId, Long excludedMemberId);


    /**
     * 팀의 팀원 행을 전부 물리 삭제한다 (GR-19, 팀 해체).
     *
     * <p>소프트 삭제가 아닌 이유는 {@code uq_team_members_membership}이 상태 조건 없는
     * 유니크이기 때문이다 — 행을 남기면 그 사람들이 어떤 팀에도 다시 들어갈 수 없다
     * (ADR space-team/0005).</p>
     */
    void deleteByTeamId(Long teamId);


    /**
     * 팀의 특정 역할 인원 수. "MASTER 정확히 1명" 불변식 검증에 쓴다 (GR-12).
     *
     * <p>부분 유니크 {@code uq_team_members_one_master}는 "최대 1명"만 보장한다.
     * "최소 1명"은 DB로 표현할 수 없어 이 카운트가 유일한 방어선이며, 반드시 위임
     * 트랜잭션 안에서 확인해야 한다.</p>
     */
    long countByTeamIdAndRole(Long teamId, TeamMemberRole role);
}
