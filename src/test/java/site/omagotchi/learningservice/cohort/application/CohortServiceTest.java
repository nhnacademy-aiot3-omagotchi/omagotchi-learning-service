package site.omagotchi.learningservice.cohort.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import site.omagotchi.learningservice.cohort.application.command.UpdateCohortCommand;
import site.omagotchi.learningservice.cohort.domain.Cohort;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;
import site.omagotchi.learningservice.cohort.infrastructure.CohortMembershipRepository;
import site.omagotchi.learningservice.cohort.infrastructure.CohortRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CohortServiceTest {

    private static final Long COHORT_ID = 2L;
    private static final UUID ACTOR_ID = new UUID(0L, 100L);
    private static final UUID FIRST_MANAGER_ID = new UUID(0L, 10L);
    private static final UUID SECOND_MANAGER_ID = new UUID(0L, 20L);

    @Mock
    private CohortRepository repository;

    @Mock
    private CohortMembershipRepository membershipRepository;

    @Mock
    private CohortAccessService accessService;

    @Mock
    private CohortManagerAssignmentPolicy managerAssignmentPolicy;

    @InjectMocks
    private CohortService service;

    @Test
    void validatesEveryActiveManagerBeforeChangingCohortPeriod() {
        Cohort cohort = Cohort.create(
                "기존 기수",
                "설명",
                LocalDate.of(2027, 1, 1),
                LocalDate.of(2027, 6, 30),
                ACTOR_ID
        );
        ReflectionTestUtils.setField(cohort, "id", COHORT_ID);

        CohortMembership firstManager = CohortMembership.activeManager(COHORT_ID, FIRST_MANAGER_ID, ACTOR_ID);
        CohortMembership secondManager = CohortMembership.activeManager(COHORT_ID, SECOND_MANAGER_ID, ACTOR_ID);
        when(repository.findById(COHORT_ID)).thenReturn(Optional.of(cohort));
        when(membershipRepository.findByCohortIdAndRoleAndStatusOrderByRequestedAtAsc(
                COHORT_ID,
                CohortMembershipRole.MANAGER,
                CohortMembershipStatus.ACTIVE
        )).thenReturn(List.of(secondManager, firstManager));

        LocalDate newStartDate = LocalDate.of(2027, 2, 1);
        LocalDate newEndDate = LocalDate.of(2027, 8, 31);
        UpdateCohortCommand command = new UpdateCohortCommand(
                "수정 기수",
                "수정 설명",
                newStartDate,
                newEndDate
        );

        var response = service.update(COHORT_ID, command, ACTOR_ID);

        assertThat(response.startDate()).isEqualTo(newStartDate);
        assertThat(response.endDate()).isEqualTo(newEndDate);
        verify(accessService).requireManager(COHORT_ID, ACTOR_ID);

        InOrder inOrder = inOrder(managerAssignmentPolicy);
        inOrder.verify(managerAssignmentPolicy).validateNoPeriodConflict(
                FIRST_MANAGER_ID,
                COHORT_ID,
                newStartDate,
                newEndDate
        );
        inOrder.verify(managerAssignmentPolicy).validateNoPeriodConflict(
                SECOND_MANAGER_ID,
                COHORT_ID,
                newStartDate,
                newEndDate
        );
    }
}
