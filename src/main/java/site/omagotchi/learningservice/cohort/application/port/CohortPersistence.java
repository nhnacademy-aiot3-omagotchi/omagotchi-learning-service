package site.omagotchi.learningservice.cohort.application.port;

import site.omagotchi.learningservice.cohort.domain.Cohort;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CohortPersistence {

    Cohort save(Cohort cohort);

    List<Cohort> findAll();

    Optional<Cohort> findById(Long cohortId);

    void delete(Cohort cohort);

    boolean existsActiveManagerPeriodConflict(
            UUID userId,
            Long targetCohortId,
            LocalDate targetStartDate,
            LocalDate targetEndDate
    );
}
