package site.omagotchi.learningservice.space.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.omagotchi.learningservice.space.infrastructure.persistence.entity.RoomOccupancyJpaEntity;

import java.time.OffsetDateTime;
import java.util.List;

public interface SpringDataRoomOccupancyRepository
        extends JpaRepository<RoomOccupancyJpaEntity, Long> {

    @Query("""
            SELECT ro
            FROM RoomOccupancyJpaEntity ro
            WHERE ro.spaceId IN :spaceIds
              AND ro.status = 'ACTIVE'
              AND ro.endedAt IS NULL
              AND ro.expiresAt > :now
            ORDER BY ro.startedAt DESC
            """)
    List<RoomOccupancyJpaEntity> findAllActiveBySpaceIds(
            @Param("spaceIds") List<Long> spaceIds,
            @Param("now") OffsetDateTime now
    );

    @Query(
            value = """
                    SELECT EXISTS (
                        SELECT 1
                        FROM learning_service.room_occupancies ro
                        WHERE ro.space_id = :spaceId
                          AND ro.status = 'ACTIVE'
                          AND ro.ended_at IS NULL
                          AND ro.expires_at > :now
                    )
                    """,
            nativeQuery = true
    )
    boolean existsActiveBySpaceId(
            @Param("spaceId") Long spaceId,
            @Param("now") OffsetDateTime now
    );
}
