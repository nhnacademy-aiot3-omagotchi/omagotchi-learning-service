package site.omagotchi.learningservice.cohort.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import site.omagotchi.learningservice.cohort.application.command.UpdateCohortCommand;
import site.omagotchi.learningservice.cohort.application.command.ChangeCohortStatusCommand;
import site.omagotchi.learningservice.cohort.application.port.CohortActiveLabQuery;
import site.omagotchi.learningservice.cohort.domain.Cohort;
import site.omagotchi.learningservice.cohort.domain.CohortStatus;
import site.omagotchi.learningservice.cohort.application.port.CohortMembershipQuery;
import site.omagotchi.learningservice.cohort.application.port.CohortPersistence;
import site.omagotchi.learningservice.cohort.application.result.CohortMembershipSummaryResult;
import site.omagotchi.learningservice.global.auth.GlobalRole;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CohortServiceTest {

    private static final Long COHORT_ID = 2L;
    private static final UUID ACTOR_ID = new UUID(0L, 100L);
    private static final UUID FIRST_MANAGER_ID = new UUID(0L, 10L);
    private static final UUID SECOND_MANAGER_ID = new UUID(0L, 20L);

    @Mock
    private CohortPersistence cohortPersistence;

    @Mock
    private CohortMembershipQuery membershipQuery;

    @Mock
    private CohortAccessService accessService;

    @Mock
    private CohortManagerAssignmentPolicy managerAssignmentPolicy;

    @Mock
    private CohortActiveLabQuery cohortActiveLabQuery;

    @Mock
    private CohortLockService cohortLockService;

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

        when(cohortPersistence.findById(COHORT_ID)).thenReturn(Optional.of(cohort));
        when(membershipQuery.findAllActiveManagerUserIds(COHORT_ID))
                .thenReturn(List.of(SECOND_MANAGER_ID, FIRST_MANAGER_ID));

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

        InOrder inOrder = inOrder(managerAssignmentPolicy, cohortPersistence, membershipQuery);
        inOrder.verify(managerAssignmentPolicy).acquireCohort(COHORT_ID);
        inOrder.verify(cohortPersistence).findById(COHORT_ID);
        inOrder.verify(membershipQuery).findAllActiveManagerUserIds(COHORT_ID);
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

    @Test
    void returnsSystemAdminCohortSummariesWithActiveMemberAndManagerAggregates() {
        Cohort cohort = Cohort.create(
                "AIoT 3기",
                "설명",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 12, 18),
                ACTOR_ID
        );
        ReflectionTestUtils.setField(cohort, "id", COHORT_ID);
        when(membershipQuery.findAllAdminSummaries()).thenReturn(List.of(
                new CohortMembershipSummaryResult(COHORT_ID, 34L, List.of(FIRST_MANAGER_ID))
        ));
        when(cohortPersistence.findAll()).thenReturn(List.of(cohort));

        var summaries = service.getAdminSummaries(GlobalRole.SYSTEM_ADMIN);

        assertThat(summaries).singleElement().satisfies(summary -> {
            assertThat(summary.id()).isEqualTo(COHORT_ID);
            assertThat(summary.memberCount()).isEqualTo(34L);
            assertThat(summary.managerUserIds()).containsExactly(FIRST_MANAGER_ID);
        });
        verify(accessService).requireSystemAdmin(GlobalRole.SYSTEM_ADMIN);
    }

    @Test
    void deletesPreparingCohortAsSystemAdmin() {
        Cohort cohort = Cohort.create(
                "준비 기수",
                "설명",
                LocalDate.of(2027, 1, 1),
                LocalDate.of(2027, 6, 30),
                ACTOR_ID
        );
        ReflectionTestUtils.setField(cohort, "id", COHORT_ID);
        when(cohortPersistence.findById(COHORT_ID)).thenReturn(Optional.of(cohort));

        service.delete(COHORT_ID, GlobalRole.SYSTEM_ADMIN);

        verify(cohortPersistence).delete(cohort);
    }

    @Test
    void rejectsDeletingActiveCohort() {
        Cohort cohort = Cohort.create(
                "운영 기수",
                "설명",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 12, 18),
                ACTOR_ID
        );
        ReflectionTestUtils.setField(cohort, "id", COHORT_ID);
        cohort.activate(true);
        when(cohortPersistence.findById(COHORT_ID)).thenReturn(Optional.of(cohort));

        assertThatThrownBy(() -> service.delete(COHORT_ID, GlobalRole.SYSTEM_ADMIN))
                .isInstanceOf(BusinessException.class);

        verify(cohortPersistence, never()).delete(cohort);
    }

    @Test
    @DisplayName("활성 실습실이 없는 기수는 운영 상태로 전환할 수 없다")
    void rejectsActivationWhenCohortHasNoActiveLab() {
        Cohort cohort = preparingCohort();
        when(cohortLockService.lockCohort(COHORT_ID)).thenReturn(cohort);
        when(membershipQuery.existsActiveManager(COHORT_ID)).thenReturn(true);
        when(cohortActiveLabQuery.existsActiveLab(COHORT_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.changeStatus(
                COHORT_ID,
                new ChangeCohortStatusCommand(CohortStatus.ACTIVE),
                GlobalRole.SYSTEM_ADMIN
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(
                        ((BusinessException) exception).getErrorCode()
                ).isEqualTo(CohortErrorCode.COHORT_ACTIVE_LAB_REQUIRED));

        assertThat(cohort.getStatus()).isEqualTo(CohortStatus.PREPARING);
    }

    @Test
    @DisplayName("공통 기수 잠금 후 활성 실습실이 있으면 기수를 운영 상태로 전환한다")
    void activatesCohortAfterCommonLockWhenActiveLabExists() {
        Cohort cohort = preparingCohort();
        when(cohortLockService.lockCohort(COHORT_ID)).thenReturn(cohort);
        when(membershipQuery.existsActiveManager(COHORT_ID)).thenReturn(true);
        when(cohortActiveLabQuery.existsActiveLab(COHORT_ID)).thenReturn(true);

        var response = service.changeStatus(
                COHORT_ID,
                new ChangeCohortStatusCommand(CohortStatus.ACTIVE),
                GlobalRole.SYSTEM_ADMIN
        );

        assertThat(response.status()).isEqualTo(CohortStatus.ACTIVE);
        verify(cohortLockService).lockCohort(COHORT_ID);
    }

    private Cohort preparingCohort() {
        Cohort cohort = Cohort.create(
                "준비 기수",
                "설명",
                LocalDate.of(2027, 1, 1),
                LocalDate.of(2027, 6, 30),
                ACTOR_ID
        );
        ReflectionTestUtils.setField(cohort, "id", COHORT_ID);
        return cohort;
    }
}
