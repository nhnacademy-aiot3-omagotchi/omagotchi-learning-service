package site.omagotchi.learningservice.cohort.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import site.omagotchi.learningservice.cohort.application.port.CohortManagerAssignmentLock;
import site.omagotchi.learningservice.cohort.application.port.CohortPersistence;
import site.omagotchi.learningservice.cohort.domain.Cohort;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CohortManagerAssignmentPolicyTest {

    private static final UUID USER_ID = UUID.fromString("019d2a48-80c0-4d6a-9a15-0b16d2dd74f1");

    @Mock
    private CohortPersistence cohortPersistence;

    @Mock
    private CohortManagerAssignmentLock assignmentLock;

    @InjectMocks
    private CohortManagerAssignmentPolicy policy;

    @Test
    void rejectsOverlappingManagerAssignmentAfterAcquiringUserLock() {
        Cohort target = cohort(2L, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 6, 30));
        when(cohortPersistence.existsActiveManagerPeriodConflict(
                USER_ID,
                2L,
                target.getStartDate(),
                target.getEndDate()
        )).thenReturn(true);

        assertThatThrownBy(() -> policy.validateNoPeriodConflict(USER_ID, target))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(CohortErrorCode.COHORT_MANAGER_PERIOD_CONFLICT));

        InOrder inOrder = inOrder(assignmentLock, cohortPersistence);
        inOrder.verify(assignmentLock).acquireCohort(2L);
        inOrder.verify(assignmentLock).acquireUser(USER_ID);
        inOrder.verify(cohortPersistence).existsActiveManagerPeriodConflict(
                USER_ID,
                2L,
                target.getStartDate(),
                target.getEndDate()
        );
    }

    @Test
    void acceptsNonOverlappingManagerAssignment() {
        Cohort target = cohort(2L, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 6, 30));
        when(cohortPersistence.existsActiveManagerPeriodConflict(
                USER_ID,
                2L,
                target.getStartDate(),
                target.getEndDate()
        )).thenReturn(false);

        assertThatCode(() -> policy.validateNoPeriodConflict(USER_ID, target))
                .doesNotThrowAnyException();
    }

    @Test
    void validatesProspectivePeriodWhenCohortPeriodChanges() {
        LocalDate newStartDate = LocalDate.of(2027, 3, 1);
        LocalDate newEndDate = LocalDate.of(2027, 9, 1);
        when(cohortPersistence.existsActiveManagerPeriodConflict(
                USER_ID,
                2L,
                newStartDate,
                newEndDate
        )).thenReturn(false);

        assertThatCode(() -> policy.validateNoPeriodConflict(
                USER_ID,
                2L,
                newStartDate,
                newEndDate
        )).doesNotThrowAnyException();

        InOrder inOrder = inOrder(assignmentLock, cohortPersistence);
        inOrder.verify(assignmentLock).acquireCohort(2L);
        inOrder.verify(assignmentLock).acquireUser(USER_ID);
        inOrder.verify(cohortPersistence).existsActiveManagerPeriodConflict(
                USER_ID,
                2L,
                newStartDate,
                newEndDate
        );
    }

    private Cohort cohort(Long id, LocalDate startDate, LocalDate endDate) {
        Cohort cohort = Cohort.create("기수", "설명", startDate, endDate, USER_ID);
        ReflectionTestUtils.setField(cohort, "id", id);
        return cohort;
    }
}
