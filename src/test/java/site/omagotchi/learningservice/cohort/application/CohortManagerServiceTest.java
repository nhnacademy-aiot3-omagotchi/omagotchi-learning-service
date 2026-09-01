package site.omagotchi.learningservice.cohort.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import site.omagotchi.learningservice.cohort.application.command.ChangeCohortMemberRoleCommand;
import site.omagotchi.learningservice.cohort.application.command.AssignCohortManagerCommand;
import site.omagotchi.learningservice.cohort.domain.Cohort;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;
import site.omagotchi.learningservice.cohort.infrastructure.CohortMembershipRepository;
import site.omagotchi.learningservice.cohort.infrastructure.CohortRepository;
import site.omagotchi.learningservice.global.auth.GlobalRole;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import site.omagotchi.learningservice.global.time.AggregationDateTime;

@ExtendWith(MockitoExtension.class)
class CohortManagerServiceTest {

    private static final UUID MANAGER_USER_ID = UUID.fromString("019d2a48-80c0-4d6a-9a15-0b16d2dd74f1");
    private static final UUID PROCESSOR_USER_ID = UUID.fromString("019d2a48-80c0-4eb7-a51d-8a427525a7d3");

    @Mock
    private CohortRepository cohortRepository;

    @Mock
    private CohortMembershipRepository membershipRepository;

    @Mock
    private CohortAccessService accessService;

    @Mock
    private CohortManagerAssignmentPolicy assignmentPolicy;

    @InjectMocks
    private CohortManagerService managerService;

    @Test
    void validatesManagerPeriodBeforeCreatingMembership() {
        Long cohortId = 1L;
        Cohort cohort = preparingCohort(cohortId);
        when(cohortRepository.findById(cohortId)).thenReturn(Optional.of(cohort));
        when(membershipRepository.findByCohortIdAndUserId(cohortId, MANAGER_USER_ID))
                .thenReturn(Optional.empty());
        when(membershipRepository.save(any(CohortMembership.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        managerService.assignManager(
                cohortId,
                new AssignCohortManagerCommand(MANAGER_USER_ID),
                PROCESSOR_USER_ID,
                GlobalRole.SYSTEM_ADMIN
        );

        InOrder inOrder = inOrder(assignmentPolicy, membershipRepository);
        inOrder.verify(assignmentPolicy).acquireCohort(cohortId);
        inOrder.verify(assignmentPolicy).validateNoPeriodConflict(MANAGER_USER_ID, cohort);
        inOrder.verify(membershipRepository).save(any(CohortMembership.class));
    }

    @Test
    void cannotChangeLastActiveManagerToMentor() {
        Long cohortId = 1L;
        UUID managerUserId = MANAGER_USER_ID;
        CohortMembership manager = activeMembership(100L, cohortId, managerUserId, CohortMembershipRole.MANAGER);

        when(cohortRepository.findById(cohortId)).thenReturn(Optional.of(preparingCohort(cohortId)));
        when(membershipRepository.findFirstByCohortIdAndUserIdAndStatusOrderByRequestedAtDesc(
                cohortId,
                managerUserId,
                CohortMembershipStatus.ACTIVE
        )).thenReturn(Optional.of(manager));
        when(membershipRepository.countByCohortIdAndRoleAndStatus(
                cohortId,
                CohortMembershipRole.MANAGER,
                CohortMembershipStatus.ACTIVE
        )).thenReturn(1L);

        assertThatThrownBy(() -> managerService.changeMemberRole(
                cohortId,
                managerUserId,
                new ChangeCohortMemberRoleCommand(CohortMembershipRole.MENTOR),
                PROCESSOR_USER_ID,
                GlobalRole.SYSTEM_ADMIN
        )).isInstanceOf(BusinessException.class);

        verify(membershipRepository, never()).changeActiveRole(any(), any(), any(), any());
    }

    private Cohort preparingCohort(Long cohortId) {
        Cohort cohort = Cohort.create(
                "AIOT 3",
                "test cohort",
                AggregationDateTime.today(),
                AggregationDateTime.today().plusDays(30),
                PROCESSOR_USER_ID
        );
        ReflectionTestUtils.setField(cohort, "id", cohortId);
        return cohort;
    }

    private CohortMembership activeMembership(
            Long membershipId,
            Long cohortId,
            UUID userId,
            CohortMembershipRole role
    ) {
        CohortMembership membership = CohortMembership.pending(cohortId, userId, role);
        ReflectionTestUtils.setField(membership, "id", membershipId);
        ReflectionTestUtils.setField(membership, "status", CohortMembershipStatus.ACTIVE);
        ReflectionTestUtils.setField(membership, "processedAt", OffsetDateTime.now());
        ReflectionTestUtils.setField(membership, "processedByUserId", PROCESSOR_USER_ID);
        return membership;
    }
}
