package site.omagotchi.learningservice.cohort.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import site.omagotchi.learningservice.cohort.application.dto.command.ApproveMembershipRequest;
import site.omagotchi.learningservice.cohort.domain.Cohort;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;
import site.omagotchi.learningservice.cohort.infrastructure.CohortJoinCodeRepository;
import site.omagotchi.learningservice.cohort.infrastructure.CohortMembershipRepository;
import site.omagotchi.learningservice.cohort.infrastructure.CohortRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CohortMembershipServiceTest {

    private static final UUID MANAGER_USER_ID = UUID.fromString("019d2a48-80c0-4d6a-9a15-0b16d2dd74f1");
    private static final UUID MEMBER_USER_ID = UUID.fromString("019d2a48-80c0-4eb7-a51d-8a427525a7d3");

    @Mock
    private CohortRepository cohortRepository;

    @Mock
    private CohortJoinCodeRepository joinCodeRepository;

    @Mock
    private CohortMembershipRepository membershipRepository;

    @Mock
    private CohortAccessService accessService;

    @InjectMocks
    private CohortMembershipService membershipService;

    @Test
    void approveMentorRequiresCohortManagerNotSystemAdmin() {
        Long cohortId = 1L;
        Long membershipId = 100L;
        UUID managerUserId = MANAGER_USER_ID;
        CohortMembership pending = CohortMembership.pending(
                cohortId,
                MEMBER_USER_ID,
                CohortMembershipRole.STUDENT
        );
        ReflectionTestUtils.setField(pending, "id", membershipId);

        when(membershipRepository.findByIdAndStatus(membershipId, CohortMembershipStatus.PENDING))
                .thenReturn(Optional.of(pending));
        when(cohortRepository.findById(cohortId)).thenReturn(Optional.of(preparingCohort(cohortId)));
        when(membershipRepository.approvePending(
                org.mockito.ArgumentMatchers.eq(membershipId),
                org.mockito.ArgumentMatchers.eq(CohortMembershipStatus.ACTIVE),
                org.mockito.ArgumentMatchers.eq(CohortMembershipRole.MENTOR),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(managerUserId)
        )).thenReturn(1);
        when(membershipRepository.findById(membershipId)).thenReturn(Optional.of(pending));

        membershipService.approve(
                membershipId,
                new ApproveMembershipRequest(CohortMembershipRole.MENTOR),
                managerUserId,
                "USER"
        );

        verify(accessService).requireManager(cohortId, managerUserId);
        verify(accessService, never()).requireSystemAdmin(org.mockito.ArgumentMatchers.any());
    }

    private Cohort preparingCohort(Long cohortId) {
        Cohort cohort = Cohort.create(
                "AIOT 3",
                "test cohort",
                LocalDate.now(),
                LocalDate.now().plusDays(30),
                MANAGER_USER_ID
        );
        ReflectionTestUtils.setField(cohort, "id", cohortId);
        return cohort;
    }
}
