package site.omagotchi.learningservice.cohort.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.cohort.application.result.UserAccessContextResult;
import site.omagotchi.learningservice.cohort.application.result.UserAccessType;
import site.omagotchi.learningservice.cohort.domain.Cohort;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;
import site.omagotchi.learningservice.cohort.domain.CohortStatus;
import site.omagotchi.learningservice.cohort.infrastructure.CohortMembershipRepository;
import site.omagotchi.learningservice.cohort.infrastructure.CohortRepository;
import site.omagotchi.learningservice.global.auth.GlobalRole;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("사용자 접근 컨텍스트")
class UserAccessContextServiceTest {

    private static final UUID USER_ID = UUID.fromString("019d2a48-80c0-4eb7-a51d-8a427525a7d3");

    @Mock
    private CohortMembershipRepository membershipRepository;

    @Mock
    private CohortRepository cohortRepository;

    @InjectMocks
    private UserAccessContextService service;

    @Test
    @DisplayName("SYSTEM_ADMIN은 기수 소속보다 전역 관리자 접근을 우선한다")
    void prioritizesSystemAdmin() {
        UserAccessContextResult result = service.getContext(USER_ID, GlobalRole.SYSTEM_ADMIN);

        assertEquals(UserAccessType.SYSTEM_ADMIN, result.accessType());
        assertEquals(List.of(), result.managedCohorts());
        assertEquals(List.of(), result.studentCohorts());
        verify(membershipRepository, never()).findByUserIdOrderByRequestedAtDesc(any());
        verify(cohortRepository, never()).findAllById(any());
    }

    @Test
    @DisplayName("PREPARING 기수의 현재 MANAGER도 관리자 접근과 관리 기수에 포함한다")
    void includesPreparingManagedCohort() {
        Cohort preparing = cohort(1L, "준비 기수", CohortStatus.PREPARING);
        Cohort activeStudentCohort = cohort(2L, "학생 기수", CohortStatus.ACTIVE);
        Cohort closed = closedCohort(3L);
        CohortMembership activeManager = membership(1L, CohortMembershipRole.MANAGER, null);
        CohortMembership activeStudent = membership(2L, CohortMembershipRole.STUDENT, null);
        CohortMembership closedManager = membership(3L, CohortMembershipRole.MANAGER, null);
        CohortMembership endedManager = membership(
                4L,
                CohortMembershipRole.MANAGER,
                OffsetDateTime.now()
        );

        when(membershipRepository.findByUserIdOrderByRequestedAtDesc(USER_ID))
                .thenReturn(List.of(
                        activeManager,
                        activeStudent,
                        closedManager,
                        endedManager
                ));
        when(cohortRepository.findAllById(any()))
                .thenReturn(List.of(preparing, activeStudentCohort, closed));

        UserAccessContextResult result = service.getContext(USER_ID, GlobalRole.USER);

        assertEquals(UserAccessType.COHORT_MANAGER, result.accessType());
        assertEquals(List.of(1L), result.managedCohorts().stream()
                .map(summary -> summary.cohortId())
                .toList());
        assertEquals(CohortStatus.PREPARING, result.managedCohorts().getFirst().status());
        assertEquals(List.of(2L), result.studentCohorts().stream()
                .map(summary -> summary.cohortId())
                .toList());
    }

    @Test
    @DisplayName("현재 STUDENT 소속만 있으면 학생 접근으로 판정한다")
    void resolvesStudent() {
        Cohort active = cohort(2L, "학생 기수", CohortStatus.ACTIVE);
        CohortMembership activeStudent = membership(2L, CohortMembershipRole.STUDENT, null);
        when(membershipRepository.findByUserIdOrderByRequestedAtDesc(USER_ID))
                .thenReturn(List.of(activeStudent));
        when(cohortRepository.findAllById(any())).thenReturn(List.of(active));

        UserAccessContextResult result = service.getContext(USER_ID, GlobalRole.USER);

        assertEquals(UserAccessType.STUDENT, result.accessType());
        assertEquals(List.of(), result.managedCohorts());
        assertEquals(List.of(2L), result.studentCohorts().stream()
                .map(summary -> summary.cohortId())
                .toList());
    }

    @Test
    @DisplayName("현재 소속이 없으면 일반 사용자 접근으로 판정한다")
    void resolvesUserWithoutCurrentMembership() {
        when(membershipRepository.findByUserIdOrderByRequestedAtDesc(USER_ID))
                .thenReturn(List.of());
        when(cohortRepository.findAllById(any())).thenReturn(List.of());

        UserAccessContextResult result = service.getContext(USER_ID, GlobalRole.USER);

        assertEquals(UserAccessType.USER, result.accessType());
        assertEquals(List.of(), result.managedCohorts());
        assertEquals(List.of(), result.studentCohorts());
    }

    private CohortMembership membership(
            Long cohortId,
            CohortMembershipRole role,
            OffsetDateTime endedAt
    ) {
        CohortMembership membership = mock(CohortMembership.class);
        when(membership.getStatus()).thenReturn(CohortMembershipStatus.ACTIVE);
        when(membership.getEndedAt()).thenReturn(endedAt);
        if (endedAt == null) {
            when(membership.getCohortId()).thenReturn(cohortId);
            when(membership.getRole()).thenReturn(role);
        }
        return membership;
    }

    private Cohort cohort(Long cohortId, String name, CohortStatus status) {
        Cohort cohort = mock(Cohort.class);
        when(cohort.getId()).thenReturn(cohortId);
        when(cohort.getName()).thenReturn(name);
        when(cohort.getStartDate()).thenReturn(LocalDate.of(2026, 9, 1));
        when(cohort.getEndDate()).thenReturn(LocalDate.of(2026, 12, 31));
        when(cohort.getStatus()).thenReturn(status);
        return cohort;
    }

    private Cohort closedCohort(Long cohortId) {
        Cohort cohort = mock(Cohort.class);
        when(cohort.getStatus()).thenReturn(CohortStatus.CLOSED);
        return cohort;
    }
}
