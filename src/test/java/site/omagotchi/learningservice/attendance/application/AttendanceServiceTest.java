package site.omagotchi.learningservice.attendance.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import site.omagotchi.learningservice.attendance.domain.AttendanceErrorCode;
import site.omagotchi.learningservice.attendance.domain.AttendanceRecord;
import site.omagotchi.learningservice.attendance.domain.AttendanceStatus;
import site.omagotchi.learningservice.attendance.infrastructure.AttendanceChangeLogRepository;
import site.omagotchi.learningservice.attendance.infrastructure.AttendanceRecordRepository;
import site.omagotchi.learningservice.attendance.infrastructure.PresenceIntervalRepository;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.cohort.domain.CohortAttendancePolicy;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;
import site.omagotchi.learningservice.cohort.infrastructure.CohortAttendancePolicyRepository;
import site.omagotchi.learningservice.cohort.infrastructure.CohortMembershipRepository;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.util.DateTimeProvider;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("출결")
@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    private static final Long COHORT_ID = 10L;
    private static final Long MEMBERSHIP_ID = 100L;
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID MANAGER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final LocalDate ATTENDANCE_DATE = LocalDate.of(2026, 7, 29);

    @Mock
    private CohortAccessService cohortAccessService;

    @Mock
    private CohortMembershipRepository membershipRepository;

    @Mock
    private CohortAttendancePolicyRepository attendancePolicyRepository;

    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;

    @Mock
    private AttendanceChangeLogRepository attendanceChangeLogRepository;

    @Mock
    private PresenceIntervalRepository presenceIntervalRepository;

    @Mock
    private DateTimeProvider dateTimeProvider;

    @InjectMocks
    private AttendanceService attendanceService;

    @Test
    @DisplayName("정시 체크인")
    void checksInOnTime() {
        Instant checkInAt = Instant.parse("2026-07-29T00:00:00Z");
        givenActiveMembership();
        given(attendancePolicyRepository.findById(COHORT_ID)).willReturn(Optional.of(policy()));
        given(dateTimeProvider.currentInstant()).willReturn(checkInAt);
        given(dateTimeProvider.calculateAggregationDate(checkInAt)).willReturn(ATTENDANCE_DATE);
        given(attendanceRecordRepository.findByCohortMembershipIdAndAttendanceDate(MEMBERSHIP_ID, ATTENDANCE_DATE))
                .willReturn(Optional.empty());
        given(attendanceRecordRepository.save(any(AttendanceRecord.class)))
                .willAnswer(invocation -> savedRecord(invocation.getArgument(0)));

        var result = attendanceService.checkIn(COHORT_ID, USER_ID);

        assertEquals(AttendanceStatus.PENDING, result.autoStatus());
        assertEquals(AttendanceStatus.PENDING, result.finalStatus());
        assertEquals(checkInAt, result.checkedInAt());
        assertEquals(0, result.lateMinutes());
        verify(presenceIntervalRepository).save(any());
    }

    @Test
    @DisplayName("지각 체크인")
    void checksInLateAfterScheduledStartTime() {
        Instant checkInAt = Instant.parse("2026-07-29T00:30:00Z");
        givenActiveMembership();
        given(attendancePolicyRepository.findById(COHORT_ID)).willReturn(Optional.of(policy()));
        given(dateTimeProvider.currentInstant()).willReturn(checkInAt);
        given(dateTimeProvider.calculateAggregationDate(checkInAt)).willReturn(ATTENDANCE_DATE);
        given(attendanceRecordRepository.findByCohortMembershipIdAndAttendanceDate(MEMBERSHIP_ID, ATTENDANCE_DATE))
                .willReturn(Optional.empty());
        given(attendanceRecordRepository.save(any(AttendanceRecord.class)))
                .willAnswer(invocation -> savedRecord(invocation.getArgument(0)));

        var result = attendanceService.checkIn(COHORT_ID, USER_ID);

        assertEquals(AttendanceStatus.LATE, result.autoStatus());
        assertEquals(30, result.lateMinutes());
    }

    @Test
    @DisplayName("중복 체크인 예외")
    void throwsWhenAlreadyCheckedIn() {
        Instant checkInAt = Instant.parse("2026-07-29T00:00:00Z");
        givenActiveMembership();
        given(attendancePolicyRepository.findById(COHORT_ID)).willReturn(Optional.of(policy()));
        given(dateTimeProvider.currentInstant()).willReturn(checkInAt);
        given(dateTimeProvider.calculateAggregationDate(checkInAt)).willReturn(ATTENDANCE_DATE);
        AttendanceRecord record = AttendanceRecord.start(MEMBERSHIP_ID, ATTENDANCE_DATE);
        record.checkIn(checkInAt, AttendanceStatus.PENDING, 0);
        given(attendanceRecordRepository.findByCohortMembershipIdAndAttendanceDate(MEMBERSHIP_ID, ATTENDANCE_DATE))
                .willReturn(Optional.of(record));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> attendanceService.checkIn(COHORT_ID, USER_ID)
        );

        assertSame(AttendanceErrorCode.ATTENDANCE_ALREADY_CHECKED_IN, exception.getErrorCode());
        verify(attendanceRecordRepository).findByCohortMembershipIdAndAttendanceDate(MEMBERSHIP_ID, ATTENDANCE_DATE);
    }

    @Test
    @DisplayName("체크아웃")
    void checksOut() {
        Instant checkInAt = Instant.parse("2026-07-29T00:00:00Z");
        Instant checkOutAt = Instant.parse("2026-07-29T09:00:00Z");
        AttendanceRecord record = AttendanceRecord.start(MEMBERSHIP_ID, ATTENDANCE_DATE);
        record.checkIn(checkInAt, AttendanceStatus.PENDING, 0);
        givenActiveMembership();
        given(dateTimeProvider.currentInstant()).willReturn(checkOutAt);
        given(dateTimeProvider.calculateAggregationDate(checkOutAt)).willReturn(ATTENDANCE_DATE);
        given(attendanceRecordRepository.findByCohortMembershipIdAndAttendanceDate(MEMBERSHIP_ID, ATTENDANCE_DATE))
                .willReturn(Optional.of(record));
        given(attendancePolicyRepository.findById(COHORT_ID)).willReturn(Optional.of(policy()));
        given(attendanceRecordRepository.save(record)).willReturn(record);

        var result = attendanceService.checkOut(COHORT_ID, USER_ID);

        assertEquals(AttendanceStatus.PRESENT, result.autoStatus());
        assertEquals(checkOutAt, result.checkedOutAt());
    }

    @Test
    @DisplayName("정책 없음 예외")
    void throwsWhenPolicyDoesNotExist() {
        givenActiveMembership();
        given(attendancePolicyRepository.findById(COHORT_ID)).willReturn(Optional.empty());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> attendanceService.checkIn(COHORT_ID, USER_ID)
        );

        assertSame(AttendanceErrorCode.ATTENDANCE_POLICY_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(attendanceRecordRepository);
    }

    @Test
    @DisplayName("관리자 일자별 조회")
    void returnsDailyRecordsForManager() {
        CohortMembership membership = activeMembership();
        AttendanceRecord record = AttendanceRecord.start(MEMBERSHIP_ID, ATTENDANCE_DATE);
        record.checkIn(Instant.parse("2026-07-29T00:00:00Z"), AttendanceStatus.PENDING, 0);
        given(membershipRepository.findByCohortIdAndStatusOrderByRequestedAtAsc(
                COHORT_ID,
                CohortMembershipStatus.ACTIVE
        )).willReturn(java.util.List.of(membership));
        given(attendanceRecordRepository.findByAttendanceDateAndCohortMembershipIdInOrderByCohortMembershipIdAsc(
                ATTENDANCE_DATE,
                java.util.List.of(MEMBERSHIP_ID)
        )).willReturn(java.util.List.of(record));

        var results = attendanceService.getDailyRecords(COHORT_ID, MANAGER_ID, ATTENDANCE_DATE);

        assertEquals(1, results.size());
        assertEquals(MEMBERSHIP_ID, results.get(0).cohortMembershipId());
    }

    private void givenActiveMembership() {
        given(cohortAccessService.requireActiveMembership(COHORT_ID, USER_ID))
                .willReturn(activeMembership());
    }

    private CohortMembership activeMembership() {
        CohortMembership membership = CohortMembership.pending(
                COHORT_ID,
                USER_ID,
                CohortMembershipRole.STUDENT
        );
        ReflectionTestUtils.setField(membership, "id", MEMBERSHIP_ID);
        ReflectionTestUtils.setField(membership, "status", CohortMembershipStatus.ACTIVE);
        return membership;
    }

    private CohortAttendancePolicy policy() {
        return CohortAttendancePolicy.create(
                COHORT_ID,
                "Asia/Seoul",
                LocalTime.of(9, 0),
                LocalTime.of(18, 0),
                LocalTime.of(10, 0),
                0,
                MANAGER_ID
        );
    }

    private AttendanceRecord savedRecord(AttendanceRecord record) {
        ReflectionTestUtils.setField(record, "id", 1L);
        return record;
    }
}
