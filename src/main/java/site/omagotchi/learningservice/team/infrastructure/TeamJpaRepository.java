package site.omagotchi.learningservice.team.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import site.omagotchi.learningservice.team.domain.Team;

import java.util.List;
import java.util.Optional;

/**
 * {@code teams}의 Spring Data 접근. Application은 이 인터페이스를 보지 않는다 —
 * {@code TeamRepository} Port를 통하고, 그 구현은 {@link TeamJpaPersistence}다.
 *
 * <p>Port가 아니라 여기에 {@code JpaRepository}가 붙는 이유는 flush 시점과 인덱스 위반
 * 변환이 기술 세부사항이기 때문이다. 이 인터페이스가 Port를 직접 구현하면
 * {@code save}의 flush 의미와 실패 변환을 표현할 수 없다.</p>
 *
 * <p>팀은 소프트 삭제이므로 "행이 있다"와 "살아 있다"가 다르다. 조회 메서드에
 * {@code DeletedAtIsNull}이 붙어 있는지 항상 확인해야 하며, 해체된 팀이 목록이나
 * 상세에 새어 나가면 안 된다. 예외는 {@link #findByIdForUpdate(Long)} 하나뿐이고
 * 그건 의도적이다.</p>
 *
 * <p>테스트는 이 인터페이스를 직접 써도 된다. Port를 우회해 DB 제약 자체를 확인하는 것이
 * 목적일 때가 있고, 컨벤션의 의존 방향 규칙은 {@code src/main}에만 적용된다.</p>
 */
public interface TeamJpaRepository extends JpaRepository<Team, Long> {

    /**
     * 단건 조회. 해체된 팀은 없는 것으로 취급한다.
     *
     * <p>락이 필요 없는 조회 경로에서만 쓴다. 락 구간과 섞으려면
     * {@link #findActiveCohortId(Long)}로 먼저 훑고 락을 잡아야 한다.</p>
     */
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

    /**
     * 내 팀 목록 조회의 마지막 단계 (GR-06).
     *
     * <p>{@code team_members}에서 뽑은 team_id 묶음을 팀 행으로 되돌리면서 해체된 팀을 걸러낸다.
     * 해체 시 팀원 행을 물리 삭제하므로 보통은 남지 않지만, 기수 종료 정리(CE-01)나
     * 회원 삭제 훅처럼 팀만 소프트 삭제되는 경로가 있어 이 필터가 필요하다.</p>
     *
     * <p>빈 리스트를 넘기면 {@code IN ()}이 되어 DB에 따라 문법 오류가 나므로,
     * 호출부가 비어 있는지 먼저 확인한다.</p>
     */
    List<Team> findByIdInAndDeletedAtIsNull(List<Long> ids);

    /** 기수별 활성 팀 식별자 (CE-01). 식별자만 읽어 1차 캐시를 오염시키지 않는다. */
    @Query("""
                SELECT team.id
                  FROM Team team
                 WHERE team.cohortId = :cohortId
                   AND team.deletedAt IS NULL
                 ORDER BY team.id ASC""")
    List<Long> findActiveIdsByCohortId(@Param("cohortId") Long cohortId);
}
