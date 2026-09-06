package site.omagotchi.learningservice.cohort.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import site.omagotchi.learningservice.cohort.application.command.SaveAttendancePolicyCommand;
import site.omagotchi.learningservice.cohort.domain.CohortAttendancePolicy;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;
import site.omagotchi.learningservice.cohort.infrastructure.CohortAttendancePolicyRepository;
import site.omagotchi.learningservice.cohort.infrastructure.CohortMembershipRepository;
import site.omagotchi.learningservice.cohort.infrastructure.CohortRepository;
import site.omagotchi.learningservice.global.auth.GlobalRole;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("기수 출결 정책 서비스")
class CohortAttendancePolicyServiceTest {

    private static final Long COHORT_ID = 1L;
    private static final UUID MANAGER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SYSTEM_ADMIN_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Mock
    private CohortRepository cohortRepository;

    @Mock
    private CohortAttendancePolicyRepository attendancePolicyRepository;

    @Mock
    private CohortMembershipRepository membershipRepository;

    @Mock
    private CohortAccessService accessService;

    @InjectMocks
    private CohortAttendancePolicyService attendancePolicyService;

    @Test
    @DisplayName("정책 조회 전 출결 정책 편집 권한을 확인한다")
    void requiresPolicyEditorWhenGettingPolicy() {
        when(attendancePolicyRepository.findById(COHORT_ID))
                .thenReturn(Optional.of(policy()));

        var result = attendancePolicyService.getPolicy(COHORT_ID, MANAGER_ID, GlobalRole.USER);

        verify(accessService).requireAttendancePolicyEditor(COHORT_ID, MANAGER_ID, GlobalRole.USER);
        assertEquals("Asia/Seoul", result.timezone());
    }

    @Test
    @DisplayName("정책이 없으면 기수 없음이 아니라 정책 미설정으로 알린다")
    void reportsPolicyNotFoundWhenPolicyMissing() {
        when(attendancePolicyRepository.findById(COHORT_ID)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> attendancePolicyService.getPolicy(COHORT_ID, MANAGER_ID, GlobalRole.USER)
        );

        assertEquals(CohortErrorCode.ATTENDANCE_POLICY_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("정책 저장 전 출결 정책 편집 권한을 확인한다")
    void requiresPolicyEditorWhenSavingPolicy() {
        when(cohortRepository.existsById(COHORT_ID)).thenReturn(true);
        when(attendancePolicyRepository.findById(COHORT_ID)).thenReturn(Optional.empty());
        when(attendancePolicyRepository.save(any(CohortAttendancePolicy.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = attendancePolicyService.savePolicy(
                COHORT_ID, command(), MANAGER_ID, GlobalRole.USER);

        verify(accessService).requireAttendancePolicyEditor(COHORT_ID, MANAGER_ID, GlobalRole.USER);
        assertEquals(30, result.allowedAwayMinutes());
    }

    @Test
    @DisplayName("전역 관리자도 기수 소속 없이 정책을 저장할 수 있다")
    void allowsSystemAdminToSavePolicy() {
        when(cohortRepository.existsById(COHORT_ID)).thenReturn(true);
        when(attendancePolicyRepository.findById(COHORT_ID)).thenReturn(Optional.empty());
        when(attendancePolicyRepository.save(any(CohortAttendancePolicy.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = attendancePolicyService.savePolicy(
                COHORT_ID, command(), SYSTEM_ADMIN_ID, GlobalRole.SYSTEM_ADMIN);

        verify(accessService)
                .requireAttendancePolicyEditor(COHORT_ID, SYSTEM_ADMIN_ID, GlobalRole.SYSTEM_ADMIN);
        assertEquals("Asia/Seoul", result.timezone());
    }

    @Test
    @DisplayName("일일 마감 정책은 ACTIVE 소속에만 공개한다")
    void returnsDailyClosingPoliciesOnlyForActiveMemberships() {
        CohortMembership active = membership(10L, CohortMembershipStatus.ACTIVE);
        CohortMembership ended = membership(20L, CohortMembershipStatus.ENDED);
        when(membershipRepository.findAllById(List.of(10L, 20L)))
                .thenReturn(List.of(active, ended));
        when(attendancePolicyRepository.findAllById(List.of(COHORT_ID)))
                .thenReturn(List.of(policy()));

        var policies = attendancePolicyService.findActiveDailyClosingPolicies(
                List.of(10L, 20L)
        );

        assertEquals(1, policies.size());
        assertEquals(LocalTime.of(18, 0), policies.get(10L).scheduledEndTime());
        assertEquals("Asia/Seoul", policies.get(10L).timezone());
    }

    private SaveAttendancePolicyCommand command() {
        return new SaveAttendancePolicyCommand(
                "Asia/Seoul",
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                LocalTime.of(10, 0),
                30
        );
    }

    private CohortAttendancePolicy policy() {
        return CohortAttendancePolicy.create(
                COHORT_ID,
                "Asia/Seoul",
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                LocalTime.of(10, 0),
                30,
                MANAGER_ID
        );
    }

    private CohortMembership membership(Long membershipId, CohortMembershipStatus status) {
        CohortMembership membership = CohortMembership.pending(
                COHORT_ID,
                UUID.randomUUID(),
                CohortMembershipRole.STUDENT
        );
        ReflectionTestUtils.setField(membership, "id", membershipId);
        ReflectionTestUtils.setField(membership, "status", status);
        return membership;
    }
}
