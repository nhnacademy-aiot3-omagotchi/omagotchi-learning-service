package site.omagotchi.learningservice.cohort.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.cohort.application.CohortErrorCode;
import site.omagotchi.learningservice.cohort.application.port.CohortPersistence;
import site.omagotchi.learningservice.cohort.domain.Cohort;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaCohortPersistence implements CohortPersistence {

    private final CohortRepository repository;

    @Override
    public Cohort save(Cohort cohort) {
        return repository.save(cohort);
    }

    @Override
    public List<Cohort> findAll() {
        return repository.findAll();
    }

    @Override
    public List<Cohort> findAllById(Collection<Long> cohortIds) {
        return repository.findAllById(cohortIds);
    }

    @Override
    public Optional<Cohort> findById(Long cohortId) {
        return repository.findById(cohortId);
    }

    @Override
    public Optional<Cohort> findByIdForUpdate(Long cohortId) {
        return repository.findByIdForUpdate(cohortId);
    }

    @Override
    public void delete(Cohort cohort) {
        try {
            repository.delete(cohort);
            repository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(CohortErrorCode.COHORT_DELETE_CONFLICT, exception);
        }
    }

    @Override
    public boolean existsActiveManagerPeriodConflict(
            UUID userId,
            Long targetCohortId,
            LocalDate targetStartDate,
            LocalDate targetEndDate
    ) {
        return repository.existsActiveManagerPeriodConflict(
                userId,
                targetCohortId,
                targetStartDate,
                targetEndDate
        );
    }
}
