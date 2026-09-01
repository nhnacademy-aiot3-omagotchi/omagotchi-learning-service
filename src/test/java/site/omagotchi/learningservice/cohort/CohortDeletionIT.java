package site.omagotchi.learningservice.cohort;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.cohort.application.CohortLockService;
import site.omagotchi.learningservice.cohort.application.CohortManagerAssignmentPolicy;
import site.omagotchi.learningservice.cohort.application.CohortService;
import site.omagotchi.learningservice.cohort.application.port.CohortActiveLabQuery;
import site.omagotchi.learningservice.cohort.application.port.CohortEventPublisher;
import site.omagotchi.learningservice.cohort.domain.Cohort;
import site.omagotchi.learningservice.cohort.application.CohortErrorCode;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.cohort.infrastructure.CohortMembershipRepository;
import site.omagotchi.learningservice.cohort.infrastructure.CohortRepository;
import site.omagotchi.learningservice.cohort.infrastructure.JpaCohortMembershipQuery;
import site.omagotchi.learningservice.cohort.infrastructure.JpaCohortPersistence;
import site.omagotchi.learningservice.global.auth.GlobalRole;
import site.omagotchi.learningservice.global.config.QueryDslConfig;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import({
        TestcontainersConfiguration.class,
        QueryDslConfig.class,
        CohortAccessService.class,
        CohortService.class,
        JpaCohortMembershipQuery.class,
        JpaCohortPersistence.class
})
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CohortDeletionIT {

    private static final UUID ADMIN_ID = new UUID(0L, 1L);
    private static final UUID MANAGER_ID = new UUID(0L, 2L);

    @Autowired
    private CohortService cohortService;

    @Autowired
    private CohortRepository cohortRepository;

    @Autowired
    private CohortMembershipRepository membershipRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private CohortManagerAssignmentPolicy managerAssignmentPolicy;

    @MockitoBean
    private CohortEventPublisher eventPublisher;

    @MockitoBean
    private CohortActiveLabQuery cohortActiveLabQuery;

    @MockitoBean
    private CohortLockService cohortLockService;

    @Test
    void deletesPreparingCohortAndCascadesItsMemberships() {
        Cohort cohort = savePreparingCohort("AIoT 4기");
        membershipRepository.saveAndFlush(
                CohortMembership.activeManager(cohort.getId(), MANAGER_ID, ADMIN_ID)
        );

        cohortService.delete(cohort.getId(), GlobalRole.SYSTEM_ADMIN);
        entityManager.clear();

        assertThat(cohortRepository.findById(cohort.getId())).isEmpty();
        assertThat(membershipRepository.findByUserIdOrderByRequestedAtDesc(MANAGER_ID)).isEmpty();
    }

    @Test
    void rejectsDeletingActiveCohortAndKeepsItsData() {
        Cohort cohort = savePreparingCohort("AIoT 3기");
        cohort.activate(true);
        cohortRepository.saveAndFlush(cohort);

        assertThatThrownBy(() -> cohortService.delete(cohort.getId(), GlobalRole.SYSTEM_ADMIN))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CohortErrorCode.COHORT_DELETE_NOT_ALLOWED);

        entityManager.clear();
        assertThat(cohortRepository.findById(cohort.getId())).isPresent();
    }

    private Cohort savePreparingCohort(String name) {
        return cohortRepository.saveAndFlush(Cohort.create(
                name,
                "Testcontainers 삭제 검증",
                LocalDate.of(2027, 1, 5),
                LocalDate.of(2027, 5, 21),
                ADMIN_ID
        ));
    }
}
