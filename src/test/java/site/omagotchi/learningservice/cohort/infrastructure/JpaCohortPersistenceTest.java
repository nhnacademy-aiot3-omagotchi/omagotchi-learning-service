package site.omagotchi.learningservice.cohort.infrastructure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import site.omagotchi.learningservice.cohort.application.CohortErrorCode;
import site.omagotchi.learningservice.cohort.domain.Cohort;
import site.omagotchi.learningservice.global.exception.BusinessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class JpaCohortPersistenceTest {

    @Mock
    private CohortRepository repository;

    @Mock
    private Cohort cohort;

    @InjectMocks
    private JpaCohortPersistence persistence;

    @Test
    void preservesDatabaseCauseWhenDeleteConflicts() {
        DataIntegrityViolationException cause = new DataIntegrityViolationException("fk conflict");
        doThrow(cause).when(repository).flush();

        BusinessException exception = catchThrowableOfType(
                BusinessException.class,
                () -> persistence.delete(cohort)
        );

        assertThat(exception.getErrorCode()).isEqualTo(CohortErrorCode.COHORT_DELETE_CONFLICT);
        assertThat(exception.getCause()).isSameAs(cause);
    }
}
