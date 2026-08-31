package site.omagotchi.learningservice.space.infrastructure.persistence.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.omagotchi.learningservice.space.infrastructure.persistence.entity.SpaceJpaEntity;

import java.util.List;
import java.util.Optional;

public interface SpringDataSpaceRepository
        extends JpaRepository<SpaceJpaEntity, Long> {

    List<SpaceJpaEntity>
    findAllByDeletedAtIsNullOrderByIdAsc();

    @Query(
            value = """
                    SELECT EXISTS (
                        SELECT 1
                        FROM learning_service.spaces s
                        WHERE LOWER(BTRIM(s.name)) = LOWER(BTRIM(:name))
                          AND s.deleted_at IS NULL
                    )
                    """,
            nativeQuery = true
    )
    boolean existsActiveByNormalizedName(
            @Param("name") String name
    );

    @Query(
            value = """
                    SELECT EXISTS (
                        SELECT 1
                        FROM learning_service.spaces s
                        WHERE LOWER(BTRIM(s.name)) = LOWER(BTRIM(:name))
                          AND s.deleted_at IS NULL
                          AND s.id <> :spaceId
                    )
                    """,
            nativeQuery = true
    )
    boolean existsActiveByNormalizedNameAndIdNot(
            @Param("name") String name,
            @Param("spaceId") Long spaceId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT space FROM SpaceJpaEntity space WHERE space.id = :spaceId")
    Optional<SpaceJpaEntity> findByIdForUpdate(
            @Param("spaceId") Long spaceId
    );

    @Query("""
                SELECT space.cohortId FROM SpaceJpaEntity space
                 WHERE space.id = :spaceId
                   AND space.deletedAt IS NULL
            """)
    Optional<Long> findCohortIdById(@Param("spaceId") Long spaceId);

    @Query("""
                SELECT (COUNT(space) > 0)
                  FROM SpaceJpaEntity space
                 WHERE space.cohortId = :cohortId
                   AND space.spaceType = site.omagotchi.learningservice.space.domain.SpaceType.LAB
                   AND space.operationalStatus = site.omagotchi.learningservice.space.domain.SpaceOperationalStatus.ACTIVE
                   AND space.deletedAt IS NULL
            """)
    boolean existsActiveLabByCohortId(@Param("cohortId") Long cohortId);

    @Query("""
                SELECT space
                  FROM SpaceJpaEntity space
                 WHERE space.cohortId = :cohortId
                   AND space.spaceType = site.omagotchi.learningservice.space.domain.SpaceType.LAB
                   AND space.operationalStatus = site.omagotchi.learningservice.space.domain.SpaceOperationalStatus.ACTIVE
                   AND space.deletedAt IS NULL
                 ORDER BY space.id ASC
            """)
    List<SpaceJpaEntity> findActiveLabsByCohortId(@Param("cohortId") Long cohortId);

    @Query("""
                SELECT COUNT(space)
                  FROM SpaceJpaEntity space
                 WHERE space.cohortId = :cohortId
                   AND space.spaceType = site.omagotchi.learningservice.space.domain.SpaceType.LAB
                   AND space.operationalStatus = site.omagotchi.learningservice.space.domain.SpaceOperationalStatus.ACTIVE
                   AND space.deletedAt IS NULL
            """)
    long countActiveLabsByCohortId(@Param("cohortId") Long cohortId);

    @Query("""
                SELECT space.name FROM SpaceJpaEntity space
                 WHERE space.id = :spaceId
                   AND space.deletedAt IS NULL
            """)
    Optional<String> findNameById(
            @Param("spaceId") Long spaceId
    );

    /** 관리 주체 일괄 해제 (CE-04). 유형을 가리지 않는다 — Port javadoc 참고. */
    @Modifying
    @Query("""
                UPDATE SpaceJpaEntity space
                   SET space.cohortId = NULL
                 WHERE space.cohortId = :cohortId""")
    int unassignByCohort(@Param("cohortId") Long cohortId);
}
