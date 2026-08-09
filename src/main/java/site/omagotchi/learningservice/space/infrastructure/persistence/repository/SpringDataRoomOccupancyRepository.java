package site.omagotchi.learningservice.space.infrastructure.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.omagotchi.learningservice.space.infrastructure.persistence.entity.RoomOccupancyJpaEntity;

import java.time.OffsetDateTime;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SpringDataRoomOccupancyRepository
        extends JpaRepository<RoomOccupancyJpaEntity, Long> {

    @Query(
            value = """
                    SELECT ro.id AS "id",
                           ro.space_id AS "spaceId",
                           ro.occupier_membership_id AS "occupierMembershipId",
                           ro.occupier_user_id AS "occupierUserId",
                           occupier_membership.cohort_id AS "occupierCohortId",
                           ro.expires_at AS "expiresAt"
                    FROM learning_service.room_occupancies ro
                    JOIN learning_service.cohort_memberships occupier_membership
                      ON occupier_membership.id = ro.occupier_membership_id
                    WHERE ro.space_id IN (:spaceIds)
                      AND ro.status = 'ACTIVE'
                      AND ro.ended_at IS NULL
                      AND ro.expires_at > :now
                    ORDER BY ro.started_at DESC
                    """,
            nativeQuery = true
    )
    List<ActiveOccupancyView> findAllActiveBySpaceIds(
            @Param("spaceIds") List<Long> spaceIds,
            @Param("now") OffsetDateTime now
    );

    @Query(
            value = """
                    SELECT participant.occupancy_id AS "occupancyId",
                           participant.user_id AS "userId"
                    FROM learning_service.occupancy_participants participant
                    JOIN learning_service.cohort_memberships participant_membership
                      ON participant_membership.id = participant.cohort_membership_id
                    WHERE participant.occupancy_id IN (:occupancyIds)
                      AND participant.left_at IS NULL
                    ORDER BY participant.id ASC
                    """,
            nativeQuery = true
    )
    List<ActiveParticipantView> findAllActiveParticipants(
            @Param("occupancyIds") List<Long> occupancyIds
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

    interface ActiveOccupancyView {
        Long getId();

        Long getSpaceId();

        Long getOccupierMembershipId();

        UUID getOccupierUserId();

        Long getOccupierCohortId();

        Instant getExpiresAt();
    }

    interface ActiveParticipantView {
        Long getOccupancyId();

        UUID getUserId();
    }
}
