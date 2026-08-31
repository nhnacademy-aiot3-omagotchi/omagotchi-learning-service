package site.omagotchi.learningservice.team.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import site.omagotchi.learningservice.team.domain.TeamMember;
import site.omagotchi.learningservice.team.domain.TeamMemberRole;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * {@code team_members}의 Spring Data 접근. Application은 {@code TeamMemberRepository} Port를
 * 통하며, 그 구현은 {@link TeamMemberJpaPersistence}다.
 *
 * <p>Port에 없는 메서드가 여기 남아 있는 것은 의도다 — 위임·해체·자동 위임(이슈 #8)이
 * 쓸 조회들이며, 해당 서비스를 구현할 때 Port로 올린다. 지금 안 쓰인다고 지우면 안 된다.</p>
 *
 * <p>이 테이블에는 상태 컬럼이 없다. 행의 존재 자체가 소속이고, 탈퇴·제외·해체는 모두
 * 물리 삭제다. 따라서 여기에는 "활성 필터"가 없으며, 소프트 삭제 메서드를 추가하면
 * 옛 행이 {@code uq_team_members_membership}을 점유해 재가입이 영구히 막힌다.</p>
 *
 * <p>주체 키가 {@code cohort_membership_id}인 것도 유의한다. 같은 사람이라도 기수가
 * 다르면 다른 값이므로, "이 계정이 팀에 있나"를 계정 id로 묻는 메서드는 여기 없다.</p>
 */
public interface TeamMemberJpaRepository extends JpaRepository<TeamMember, Long> {

    /**
     * 이 멤버십이 이미 어떤 팀에든 소속됐는지 (GR-18).
     *
     * <p>팀 id를 받지 않는 것이 핵심이다. 멤버십은 기수당 1행이므로 팀을 가리지 않는
     * 이 검사가 곧 "기수당 1인 1팀"이 된다. 다른 기수의 팀에 소속된 것은 다른 멤버십이라
     * 여기 걸리지 않으며, 그게 정상이다.</p>
     */
    boolean existsByCohortMembershipId(Long cohortMembershipId);          // GR-18

    /**
     * 멤버십이 속한 팀원 행. 팀을 몰라도 소속을 찾을 수 있다.
     *
     * <p>회원 삭제 훅(#8)처럼 "이 사람이 어느 팀에 있는지" 부터 알아내야 하는 경로용이다.
     * 단독 유니크 덕분에 결과는 최대 1건이다.</p>
     */
    Optional<TeamMember> findByCohortMembershipId(Long cohortMembershipId);

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
     * 이 멤버십이 속한 팀 식별자만 읽는다 (GR-16).
     *
     * <p>엔티티가 아니라 스칼라인 것이 요점이다. {@code TeamMember}를 읽으면 1차 캐시에
     * 올라가 뒤이은 {@code FOR UPDATE}가 락 이전 스냅샷을 돌려준다 —
     * {@code TeamJpaRepository.findActiveCohortId}와 같은 이유다.</p>
     */
    @Query("""
            select member.teamId
              from TeamMember member
             where member.cohortMembershipId = :cohortMembershipId
            """)
    Optional<Long> findTeamIdByCohortMembershipId(
            @Param("cohortMembershipId") Long cohortMembershipId);

    /**
     * 팀 현재 인원 (GR-17).
     *
     * <p>반드시 {@code teams} 행 락을 잡은 트랜잭션 안에서 호출해야 한다.
     * 락 밖에서 세면 7명 팀에 두 요청이 동시에 들어와 9명이 된다 —
     * "최대 8행"은 유니크 인덱스로 표현할 수 없어 DB가 잡아주지 못한다.</p>
     */
    long countByTeamId(Long teamId);                                      // GR-17

    /**
     * 역할별 인원. MASTER가 정확히 1명인지 커밋 전에 확인하는 용도다 (GR-12).
     *
     * <p>{@code uq_team_members_one_master}는 "최대 1명"만 보장하므로 0명은 통과한다.
     * 위임·탈퇴 트랜잭션이 이 카운트로 "최소 1명"을 직접 지켜야 한다.</p>
     */
    long countByTeamIdAndRole(Long teamId, TeamMemberRole role);          // GR-12 불변식 검증

    /**
     * 팀원 목록 (GR-15). 가입 순으로 반환한다.
     *
     * <p>표시 순서(마스터 우선)는 서비스가 다시 정렬한다. 여기서 joined_at 순인 것은
     * 자동 위임 기준(GR-16: joined_at 최소)과 같은 축을 쓰기 위해서다.</p>
     */
    List<TeamMember> findByTeamIdOrderByJoinedAtAsc(Long teamId);         // GR-15

    /**
     * 팀 해체 시 팀원 전 행 물리 삭제 (GR-19).
     *
     * <p>파생 delete 쿼리라 엔티티를 하나씩 로드한 뒤 지운다. 정원이 8명이라
     * 성능 문제는 없고, 오히려 영속성 컨텍스트와 상태가 어긋나지 않아 안전하다.</p>
     *
     * <p>팀 자체는 {@code deleted_at}만 기록하고 행을 남긴다 — 지우는 건 팀원 쪽뿐이다.</p>
     */
    void deleteByTeamId(Long teamId);                                     // GR-19

    /**
     * 위임 트랜잭션 진입점 (GR-20).
     * 반드시 id 오름차순으로 잠근다 — 정렬하지 않으면 두 위임 요청이
     * 반대 순서로 행을 잠가 데드락이 난다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from TeamMember m where m.teamId = :teamId order by m.id asc")
    List<TeamMember> findAllByTeamIdForUpdate(@Param("teamId") Long teamId);

    /**
     * 정합성 스윕의 배치 조회 (ADR 0013).
     *
     * <p>Projection인 것이 요점이다. 엔티티로 읽으면 그 인스턴스가 1차 캐시에 올라가고,
     * 뒤이은 정리의 {@code FOR UPDATE}가 락 이전 스냅샷을 돌려준다 —
     * {@link #findTeamIdByCohortMembershipId}와 같은 함정이다.</p>
     *
     * <p>{@code cohort_memberships}를 조인하지 않는다. 소속 유효성은 기수 파트의 공개
     * 계약에 묻는다 (Port javadoc 참고).</p>
     */
    @Query("""
            select member.id as teamMemberId,
                   member.cohortMembershipId as cohortMembershipId
              from TeamMember member
             where member.id > :afterId
             order by member.id asc
            """)
    List<MembershipRefProjection> findMembershipRefsAfter(
            @Param("afterId") Long afterId,
            Pageable pageable
    );

    /** 닫힌 Projection. 필드를 늘리면 select 컬럼이 함께 늘어난다. */
    interface MembershipRefProjection {
        Long getTeamMemberId();

        Long getCohortMembershipId();
    }

    /**
     * 자동 위임 대상 선정 기준 (GR-16): joined_at 최소, 동률 시 id 최소.
     *
     * <p>정렬이 결정적이어야 하는 이유는 훅이 재시도될 수 있기 때문이다. joined_at만으로
     * 정렬하면 같은 시각에 추가된 팀원들 사이에서 순서가 매번 달라져, 두 번째 실행이
     * 첫 번째와 다른 사람을 MASTER로 만들 수 있다. id를 tie-breaker로 둬야 멱등해진다.</p>
     *
     * <p>{@code LIMIT 1}이 아니라 목록을 돌려주는 것은, 후보가 정말 없는 경우
     * (= 팀에 남은 사람이 없으니 팀을 소프트 삭제해야 하는 경우)를 호출부가 빈 리스트로
     * 자연스럽게 구분하게 하려는 것이다. 호출부는 첫 원소만 쓴다.</p>
     *
     * @param excludedMemberId 떠나는 사람의 {@code team_members.id}. 아직 행이 남아 있을 수 있어 명시적으로 제외한다
     */
    @Query("""
            select m
              from TeamMember m
             where m.teamId = :teamId
               and m.id <> :excludedMemberId
             order by m.joinedAt asc, m.id asc
            """)
    List<TeamMember> findSuccessorCandidates(
            @Param("teamId") Long teamId,
            @Param("excludedMemberId") Long excludedMemberId
    );
}
