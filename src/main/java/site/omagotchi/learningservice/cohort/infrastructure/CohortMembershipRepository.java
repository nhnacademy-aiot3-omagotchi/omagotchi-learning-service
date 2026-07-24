package site.omagotchi.learningservice.cohort.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CohortMembershipRepository extends JpaRepository<CohortMembership, Long> {

    boolean existsByCohortIdAndUserIdAndStatusIn(
            Long cohortId,
            UUID userId,
            Collection<CohortMembershipStatus> statuses
    );

    boolean existsByIdAndStatus(Long id, CohortMembershipStatus status);

    Optional<CohortMembership> findByIdAndStatus(Long id, CohortMembershipStatus status);

    Optional<CohortMembership> findFirstByCohortIdAndUserIdAndStatusOrderByRequestedAtDesc(
            Long cohortId,
            UUID userId,
            CohortMembershipStatus status
    );

    Optional<CohortMembership> findFirstByCohortIdAndUserIdAndStatusInOrderByRequestedAtDesc(
            Long cohortId,
            UUID userId,
            Collection<CohortMembershipStatus> statuses
    );

    Optional<CohortMembership> findByCohortIdAndUserId(Long cohortId, UUID userId);

    List<CohortMembership> findByUserIdOrderByRequestedAtDesc(UUID userId);

    List<CohortMembership> findByCohortIdAndStatusOrderByRequestedAtAsc(
            Long cohortId,
            CohortMembershipStatus status
    );

    List<CohortMembership> findByCohortIdOrderByRequestedAtAsc(Long cohortId);

    boolean existsByCohortIdAndRoleAndStatus(
            Long cohortId,
            CohortMembershipRole role,
            CohortMembershipStatus status
    );

    boolean existsByCohortIdAndUserIdAndRoleAndStatus(
            Long cohortId,
            UUID userId,
            CohortMembershipRole role,
            CohortMembershipStatus status
    );

    boolean existsByUserIdAndRoleAndStatusAndEndedAtIsNull(
            UUID userId,
            CohortMembershipRole role,
            CohortMembershipStatus status
    );

    long countByCohortIdAndRoleAndStatus(
            Long cohortId,
            CohortMembershipRole role,
            CohortMembershipStatus status
    );

    default boolean existsActiveManagerByCohortId(Long cohortId) {
        return existsByCohortIdAndRoleAndStatus(
                cohortId,
                CohortMembershipRole.MANAGER,
                CohortMembershipStatus.ACTIVE
        );
    }

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update CohortMembership membership
               set membership.status = :status,
                   membership.role = :role,
                   membership.processedAt = :processedAt,
                   membership.processedByUserId = :processedByUserId
             where membership.id = :membershipId
               and membership.status = site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus.PENDING
            """)
    int approvePending(
            @Param("membershipId") Long membershipId,
            @Param("status") CohortMembershipStatus status,
            @Param("role") CohortMembershipRole role,
            @Param("processedAt") OffsetDateTime processedAt,
            @Param("processedByUserId") UUID processedByUserId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update CohortMembership membership
               set membership.role = :role,
                   membership.processedAt = :processedAt,
                   membership.processedByUserId = :processedByUserId
             where membership.id = :membershipId
               and membership.status = site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus.ACTIVE
            """)
    int changeActiveRole(
            @Param("membershipId") Long membershipId,
            @Param("role") CohortMembershipRole role,
            @Param("processedAt") OffsetDateTime processedAt,
            @Param("processedByUserId") UUID processedByUserId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update CohortMembership membership
               set membership.status = :status,
                   membership.rejectionReason = :rejectionReason,
                   membership.processedAt = :processedAt,
                   membership.processedByUserId = :processedByUserId
             where membership.id = :membershipId
               and membership.status = site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus.PENDING
            """)
    int rejectPending(
            @Param("membershipId") Long membershipId,
            @Param("status") CohortMembershipStatus status,
            @Param("rejectionReason") String rejectionReason,
            @Param("processedAt") OffsetDateTime processedAt,
            @Param("processedByUserId") UUID processedByUserId
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update CohortMembership membership
               set membership.status = site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus.PENDING,
                   membership.role = site.omagotchi.learningservice.cohort.domain.CohortMembershipRole.STUDENT,
                   membership.requestedAt = :requestedAt,
                   membership.processedAt = null,
                   membership.processedByUserId = null,
                   membership.rejectionReason = null,
                   membership.endedAt = null
             where membership.id = :membershipId
               and membership.status = site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus.REJECTED
            """)
    int requestAgainRejected(
            @Param("membershipId") Long membershipId,
            @Param("requestedAt") OffsetDateTime requestedAt
    );
}
