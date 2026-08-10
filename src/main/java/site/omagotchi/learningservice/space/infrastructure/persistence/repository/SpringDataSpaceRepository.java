package site.omagotchi.learningservice.space.infrastructure.persistence.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    Optional<SpaceJpaEntity>
    findByIdAndDeletedAtIsNull(
            Long spaceId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT space FROM SpaceJpaEntity space WHERE space.id = :spaceId")
    Optional<SpaceJpaEntity> findByIdForUpdate(
            @Param("spaceId") Long spaceId
    );
}
