package site.omagotchi.learningservice.cohort.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
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

public interface CohortMembershipRepository extends
        JpaRepository<CohortMembership, Long>,
        CohortMembershipRepositoryCustom {

    boolean existsByCohortIdAndUserIdAndStatusIn(
            Long cohortId,
            UUID userId,
            Collection<CohortMembershipStatus> statuses
    );

    boolean existsByIdAndStatus(Long id, CohortMembershipStatus status);

    Optional<CohortMembership> findByIdAndStatus(Long id, CohortMembershipStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select membership
            from CohortMembership membership
            where membership.id = :id
              and membership.status = :status
            """)
    Optional<CohortMembership> findWithLockByIdAndStatus(
            Long id,
            CohortMembershipStatus status
    );

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

    Optional<CohortMembership> findFirstByUserIdAndStatusAndEndedAtIsNullOrderByRequestedAtDesc(
            UUID userId,
            CohortMembershipStatus status
    );

    List<CohortMembership> findByCohortIdAndStatusOrderByRequestedAtAsc(
            Long cohortId,
            CohortMembershipStatus status
    );

    List<CohortMembership> findByCohortIdAndRoleAndStatusOrderByRequestedAtAsc(
            Long cohortId,
            CohortMembershipRole role,
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

    boolean existsByCohortIdAndUserIdAndRoleInAndStatus(
            Long cohortId,
            UUID userId,
            Collection<CohortMembershipRole> roles,
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

    /**
     * ACTIVE 소속을 종료 상태로 전이한다.
     *
     * <p>{@code status}와 {@code endedAt}을 반드시 함께 쓴다 —
     * {@code ck_cohort_memberships_ended_at}이
     * {@code status = 'ENDED' ⟹ ended_at IS NOT NULL}을 강제하므로 한쪽만 바꾸면
     * 커밋이 거부된다.</p>
     *
     * <p><b>ACTIVE만 대상으로 삼는 것이 의도다.</b> PENDING 행을 여기로 보내면
     * {@code ck_cohort_memberships_processed}가 요구하는 {@code processed_at}·
     * {@code processed_by_user_id}가 비어 있어 위반이 된다. 계정 삭제·기수 종료 같은
     * 시스템 종료에는 처리자 계정이 없으므로, 그 둘이 이미 채워진 ACTIVE만 다룬다.</p>
     *
     * <p>조건부 UPDATE라 멱등하다. 같은 종료 훅이 두 번 도착해도 두 번째는 0행이며,
     * 이미 기록된 {@code ended_at}을 덮어쓰지 않는다 — 재전달이 전제인 진입점이라
     * 이 성질이 계약의 일부다.</p>
     *
     * @return 이번 호출로 종료됐으면 1, 이미 ACTIVE가 아니었으면 0
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update CohortMembership membership
               set membership.status = site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus.ENDED,
                   membership.endedAt = :endedAt
             where membership.id = :membershipId
               and membership.status = site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus.ACTIVE
            """)
    int endActive(
            @Param("membershipId") Long membershipId,
            @Param("endedAt") OffsetDateTime endedAt
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
