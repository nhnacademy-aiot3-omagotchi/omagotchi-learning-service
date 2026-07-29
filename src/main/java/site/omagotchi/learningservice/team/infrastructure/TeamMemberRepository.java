package site.omagotchi.learningservice.team.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import site.omagotchi.learningservice.team.domain.TeamMember;
import site.omagotchi.learningservice.team.domain.TeamMemberRole;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {

    boolean existsByCohortMembershipId(Long cohortMembershipId);          // GR-18

    Optional<TeamMember> findByCohortMembershipId(Long cohortMembershipId);

    List<TeamMember> findByCohortMembershipIdIn(Collection<Long> cohortMembershipIds);

    Optional<TeamMember> findByTeamIdAndCohortMembershipId(Long teamId, Long cohortMembershipId);

    long countByTeamId(Long teamId);                                      // GR-17

    long countByTeamIdAndRole(Long teamId, TeamMemberRole role);          // GR-12 불변식 검증

    List<TeamMember> findByTeamIdOrderByJoinedAtAsc(Long teamId);         // GR-15

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
     * 자동 위임 대상 선정 기준 (GR-16): joined_at 최소, 동률 시 id 최소.
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