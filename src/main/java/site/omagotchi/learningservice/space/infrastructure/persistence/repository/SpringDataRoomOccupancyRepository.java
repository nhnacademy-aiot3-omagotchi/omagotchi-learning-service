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
}