package site.omagotchi.learningservice.cohort.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.cohort.application.port.CohortManagerAssignmentLock;
import site.omagotchi.learningservice.cohort.application.port.CohortPersistence;
import site.omagotchi.learningservice.cohort.domain.Cohort;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.time.LocalDate;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CohortManagerAssignmentPolicy {

    private final CohortPersistence cohortPersistence;
    private final CohortManagerAssignmentLock assignmentLock;

    public void acquireCohort(Long cohortId) {
        assignmentLock.acquireCohort(cohortId);
    }

    /**
     * 종료일과 다음 시작일이 같은 경계는 겹치지 않는 것으로 본다.
     */
    public void validateNoPeriodConflict(UUID userId, Cohort targetCohort) {
        validateNoPeriodConflict(
                userId,
                targetCohort.getId(),
                targetCohort.getStartDate(),
                targetCohort.getEndDate()
        );
    }

    /**
     * 기수 운영 기간 수정 전에도 동일한 규칙을 적용한다.
     */
    public void validateNoPeriodConflict(
            UUID userId,
            Long targetCohortId,
            LocalDate targetStartDate,
            LocalDate targetEndDate
    ) {
        assignmentLock.acquireCohort(targetCohortId);
        assignmentLock.acquireUser(userId);

        if (cohortPersistence.existsActiveManagerPeriodConflict(
                userId,
                targetCohortId,
                targetStartDate,
                targetEndDate
        )) {
            throw new BusinessException(CohortErrorCode.COHORT_MANAGER_PERIOD_CONFLICT);
        }
    }
}
