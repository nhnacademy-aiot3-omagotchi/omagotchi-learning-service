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
               and lower(trim(t.name)) = lower(trim(:name))
               and t.deletedAt is null
            """)
    boolean existsActiveByCohortIdAndName(
            @Param("cohortId") Long cohortId,
            @Param("name") String name
    );

    /**
     * 팀의 기수만 스칼라로 읽는다.
     *
     * 엔티티가 아니라 값 하나만 뽑는 것이 이 쿼리의 존재 이유다.
     * Team을 엔티티로 먼저 읽으면 1차 캐시에 올라가고, 뒤이은 findByIdForUpdate가
     * SELECT ... FOR UPDATE를 실제로 실행해도 Hibernate는 캐시의 인스턴스를 그대로
     * 돌려준다(재조회 결과로 필드를 덮어쓰지 않는다). 그러면 락 획득 후 deleted_at
     * 재확인이 락 이전 스냅샷을 보게 되어 해체 레이스 방어가 무력화된다.
     */
    @Query("""
            select t.cohortId
              from Team t
             where t.id = :id
               and t.deletedAt is null
            """)
    Optional<Long> findActiveCohortId(@Param("id") Long id);

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