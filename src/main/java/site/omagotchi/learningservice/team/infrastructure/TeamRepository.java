package site.omagotchi.learningservice.team.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import site.omagotchi.learningservice.team.domain.Team;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {

    Optional<Team> findByIdAndDeletedAtIsNull(Long id);

    /**
     * 기수 내 활성 팀 이름 중복 확인 (GR-21).
     * uq_teams_active_name이 LOWER(BTRIM(name)) 기준이므로 쿼리도 같은 기준을 쓴다.
     * name은 Team.normalizeName()으로 정규화한 값을 넘긴다.
     */
    @Query("""
            select count(t) > 0
              from Team t
             where t.cohortId = :cohortId
               and lower(t.name) = lower(:name)
               and t.deletedAt is null
            """)
    boolean existsActiveByCohortIdAndName(
            @Param("cohortId") Long cohortId,
            @Param("name") String name
    );

    /**
     * 팀 행 배타 락. 정원 카운트(GR-17)와 해체 레이스 방어의 유일한 수단이다.
     * "최대 N행"은 유니크 인덱스로 표현할 수 없으므로 카운트는 반드시 이 락 안에서 한다.
     *
     * deleted_at 조건을 쿼리에 넣지 않는 것이 의도다 — 락을 잡은 뒤 재확인해야
     * "해체 커밋 직후 도착한 추가 요청"을 404로 잡아낼 수 있다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Team t where t.id = :id")
    Optional<Team> findByIdForUpdate(@Param("id") Long id);

    List<Team> findByIdInAndDeletedAtIsNull(List<Long> ids);
}