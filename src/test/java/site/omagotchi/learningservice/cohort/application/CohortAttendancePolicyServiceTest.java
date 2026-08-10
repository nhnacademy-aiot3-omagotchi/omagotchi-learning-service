package site.omagotchi.learningservice.cohort.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.cohort.application.command.SaveAttendancePolicyCommand;
import site.omagotchi.learningservice.cohort.domain.CohortAttendancePolicy;
import site.omagotchi.learningservice.cohort.infrastructure.CohortAttendancePolicyRepository;
import site.omagotchi.learningservice.cohort.infrastructure.CohortRepository;

import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("기수 출결 정책 서비스")
class CohortAttendancePolicyServiceTest {

    private static final Long COHORT_ID = 1L;
    private static final UUID MANAGER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private CohortRepository cohortRepository;

    @Mock
    private CohortAttendancePolicyRepository attendancePolicyRepository;

    @Mock
    private CohortAccessService accessService;

    @InjectMocks
    private CohortAttendancePolicyService attendancePolicyService;

    @Test
    @DisplayName("정책 조회 전 기수 관리자 권한을 확인한다")
    void requiresManagerWhenGettingPolicy() {
        when(attendancePolicyRepository.findById(COHORT_ID))
                .thenReturn(Optional.of(policy()));

        var result = attendancePolicyService.getPolicy(COHORT_ID, MANAGER_ID);

        verify(accessService).requireManager(COHORT_ID, MANAGER_ID);
        assertEquals("Asia/Seoul", result.timezone());
    }

    @Test
    @DisplayName("정책 저장 전 기수 관리자 권한을 확인한다")
    void requiresManagerWhenSavingPolicy() {
        SaveAttendancePolicyCommand command = new SaveAttendancePolicyCommand(
                "Asia/Seoul",
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                LocalTime.of(10, 0),
                30
        );
        when(cohortRepository.existsById(COHORT_ID)).thenReturn(true);
        when(attendancePolicyRepository.findById(COHORT_ID)).thenReturn(Optional.empty());
        when(attendancePolicyRepository.save(any(CohortAttendancePolicy.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = attendancePolicyService.savePolicy(COHORT_ID, command, MANAGER_ID);

        verify(accessService).requireManager(COHORT_ID, MANAGER_ID);
        assertEquals(30, result.allowedAwayMinutes());
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
}
