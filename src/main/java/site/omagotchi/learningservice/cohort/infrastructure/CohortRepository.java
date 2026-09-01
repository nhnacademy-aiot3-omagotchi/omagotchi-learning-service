package site.omagotchi.learningservice.cohort.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.omagotchi.learningservice.cohort.domain.Cohort;
import site.omagotchi.learningservice.cohort.domain.CohortStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CohortRepository extends JpaRepository<Cohort, Long> {

    List<Cohort> findByStatus(CohortStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT cohort FROM Cohort cohort WHERE cohort.id = :cohortId")
    Optional<Cohort> findByIdForUpdate(@Param("cohortId") Long cohortId);

    @Query("""
            select (count(cohort) > 0)
            from Cohort cohort, CohortMembership membership
            where membership.cohortId = cohort.id
              and membership.userId = :userId
              and membership.role = site.omagotchi.learningservice.cohort.domain.CohortMembershipRole.MANAGER
              and membership.status = site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus.ACTIVE
              and cohort.status <> site.omagotchi.learningservice.cohort.domain.CohortStatus.CLOSED
              and cohort.id <> :targetCohortId
              and cohort.startDate < :targetEndDate
              and :targetStartDate < cohort.endDate
            """)
    boolean existsActiveManagerPeriodConflict(
            @Param("userId") UUID userId,
            @Param("targetCohortId") Long targetCohortId,
            @Param("targetStartDate") LocalDate targetStartDate,
            @Param("targetEndDate") LocalDate targetEndDate
    );
}
