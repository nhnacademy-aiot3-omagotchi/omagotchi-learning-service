package site.omagotchi.learningservice.attendance.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import site.omagotchi.learningservice.attendance.application.AttendanceErrorCode;
import site.omagotchi.learningservice.attendance.application.command.ChangeAttendanceStatusCommand;
import site.omagotchi.learningservice.attendance.application.query.AttendancePageQuery;
import site.omagotchi.learningservice.attendance.application.event.AttendanceCheckedInEvent;
import site.omagotchi.learningservice.attendance.application.port.AttendanceEventPublisher;
import site.omagotchi.learningservice.attendance.application.port.AttendanceRecordQueryRepository;
import site.omagotchi.learningservice.attendance.domain.AttendanceRecord;
import site.omagotchi.learningservice.attendance.domain.AttendanceStatus;
import site.omagotchi.learningservice.attendance.infrastructure.AttendanceChangeLogRepository;
import site.omagotchi.learningservice.attendance.infrastructure.AttendanceRecordRepository;
import site.omagotchi.learningservice.attendance.infrastructure.PresenceIntervalRepository;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.cohort.application.CohortMembershipQueryService;
import site.omagotchi.learningservice.cohort.domain.CohortAttendancePolicy;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;
import site.omagotchi.learningservice.cohort.infrastructure.CohortAttendancePolicyRepository;
import site.omagotchi.learningservice.cohort.infrastructure.CohortMembershipRepository;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
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
    private CohortMembershipQueryService cohortMembershipQueryService;

    @Mock
    private CohortMembershipRepository membershipRepository;

    @Mock
    private CohortAttendancePolicyRepository attendancePolicyRepository;

    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;

    @Mock
    private AttendanceRecordQueryRepository attendanceRecordQueryRepository;

    @Mock
    private AttendanceChangeLogRepository attendanceChangeLogRepository;

    @Mock
    private PresenceIntervalRepository presenceIntervalRepository;

    @Mock
    private AttendanceEventPublisher attendanceEventPublisher;

    @Mock
    private Clock clock;

    @InjectMocks
    private AttendanceService attendanceService;

    @Test
    @DisplayName("정시 체크인")
    void checksInOnTime() {
        Instant checkInAt = Instant.parse("2026-07-29T00:00:00Z");
        givenActiveMembership();
        given(attendancePolicyRepository.findById(COHORT_ID)).willReturn(Optional.of(policy()));
        given(clock.instant()).willReturn(checkInAt);
        given(attendanceRecordRepository.findWithLockByCohortMembershipIdAndAttendanceDate(MEMBERSHIP_ID, ATTENDANCE_DATE))
                .willReturn(Optional.empty());
        given(attendanceRecordRepository.save(any(AttendanceRecord.class)))
                .willAnswer(invocation -> savedRecord(invocation.getArgument(0)));

        var result = attendanceService.checkIn(COHORT_ID, USER_ID);

        assertEquals(AttendanceStatus.PENDING, result.autoStatus());
        assertEquals(AttendanceStatus.PENDING, result.finalStatus());
        assertEquals(checkInAt, result.checkedInAt());
        assertEquals(0, result.lateMinutes());
        InOrder inOrder = inOrder(membershipRepository, attendanceRecordRepository);
        inOrder.verify(membershipRepository).findWithLockByIdAndStatus(
                MEMBERSHIP_ID,
                CohortMembershipStatus.ACTIVE
        );
        inOrder.verify(attendanceRecordRepository).findWithLockByCohortMembershipIdAndAttendanceDate(
                MEMBERSHIP_ID,
                ATTENDANCE_DATE
        );
        verify(presenceIntervalRepository).save(any());
        verify(attendanceEventPublisher).publishCheckedIn(new AttendanceCheckedInEvent(
                USER_ID,
                COHORT_ID,
                1L,
                checkInAt
        ));
    }

    @Test
    @DisplayName("지각 체크인")
    void checksInLateAfterScheduledStartTime() {
        Instant checkInAt = Instant.parse("2026-07-29T00:30:00Z");
        givenActiveMembership();
        given(attendancePolicyRepository.findById(COHORT_ID)).willReturn(Optional.of(policy()));
        given(clock.instant()).willReturn(checkInAt);
        given(attendanceRecordRepository.findWithLockByCohortMembershipIdAndAttendanceDate(MEMBERSHIP_ID, ATTENDANCE_DATE))
                .willReturn(Optional.empty());
        given(attendanceRecordRepository.save(any(AttendanceRecord.class)))
                .willAnswer(invocation -> savedRecord(invocation.getArgument(0)));

        var result = attendanceService.checkIn(COHORT_ID, USER_ID);

        assertEquals(AttendanceStatus.LATE, result.autoStatus());
        assertEquals(30, result.lateMinutes());
    }

    @Test
    @DisplayName("중복 체크인은 기존 기록을 반환")
    void returnsExistingRecordWhenAlreadyCheckedIn() {
        Instant checkInAt = Instant.parse("2026-07-29T00:00:00Z");
        givenActiveMembership();
        given(attendancePolicyRepository.findById(COHORT_ID)).willReturn(Optional.of(policy()));
        given(clock.instant()).willReturn(checkInAt);
        AttendanceRecord record = AttendanceRecord.start(MEMBERSHIP_ID, ATTENDANCE_DATE);
        record.checkIn(checkInAt, AttendanceStatus.PENDING, 0);
        given(attendanceRecordRepository.findWithLockByCohortMembershipIdAndAttendanceDate(MEMBERSHIP_ID, ATTENDANCE_DATE))
                .willReturn(Optional.of(record));

        var result = attendanceService.checkIn(COHORT_ID, USER_ID);

        assertEquals(checkInAt, result.checkedInAt());
        verify(attendanceRecordRepository).findWithLockByCohortMembershipIdAndAttendanceDate(
                MEMBERSHIP_ID,
                ATTENDANCE_DATE
        );
        verifyNoInteractions(attendanceEventPublisher);
    }

    @Test
    @DisplayName("체크아웃")
    void checksOut() {
        Instant checkInAt = Instant.parse("2026-07-29T00:00:00Z");
        Instant checkOutAt = Instant.parse("2026-07-29T09:00:00Z");
        AttendanceRecord record = AttendanceRecord.start(MEMBERSHIP_ID, ATTENDANCE_DATE);
        record.checkIn(checkInAt, AttendanceStatus.PENDING, 0);
        givenActiveMembership();
        given(clock.instant()).willReturn(checkOutAt);
        given(attendanceRecordRepository.findWithLockByCohortMembershipIdAndAttendanceDate(MEMBERSHIP_ID, ATTENDANCE_DATE))
                .willReturn(Optional.of(record));
        given(attendancePolicyRepository.findById(COHORT_ID)).willReturn(Optional.of(policy()));
        given(presenceIntervalRepository.findByAttendanceIdOrderByStartedAtAsc(any())).willReturn(java.util.List.of());
        given(attendanceRecordRepository.save(record)).willReturn(record);

        var result = attendanceService.checkOut(COHORT_ID, USER_ID);

        assertEquals(AttendanceStatus.PRESENT, result.autoStatus());
        assertEquals(checkOutAt, result.checkedOutAt());
    }

    @Test
    @DisplayName("중복 체크아웃은 기존 기록을 반환")
    void returnsExistingRecordWhenAlreadyCheckedOut() {
        Instant checkInAt = Instant.parse("2026-07-29T00:00:00Z");
        Instant checkOutAt = Instant.parse("2026-07-29T09:00:00Z");
        AttendanceRecord record = AttendanceRecord.start(MEMBERSHIP_ID, ATTENDANCE_DATE);
        record.checkIn(checkInAt, AttendanceStatus.PENDING, 0);
        record.checkOut(checkOutAt, AttendanceStatus.PRESENT, 0);
        givenActiveMembership();
        given(clock.instant()).willReturn(checkOutAt.plusSeconds(60));
        given(attendanceRecordRepository.findWithLockByCohortMembershipIdAndAttendanceDate(
                MEMBERSHIP_ID,
                ATTENDANCE_DATE
        )).willReturn(Optional.of(record));

        var result = attendanceService.checkOut(COHORT_ID, USER_ID);

        assertEquals(checkOutAt, result.checkedOutAt());
        assertEquals(AttendanceStatus.PRESENT, result.autoStatus());
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
        AttendancePageQuery query = AttendancePageQuery.of(ATTENDANCE_DATE, ATTENDANCE_DATE, 0, 20);
        // 정렬과 Pageable 생성은 infrastructure 책임이므로 여기에서 검증하지 않는다.
        given(attendanceRecordQueryRepository.findDailyRecords(
                ATTENDANCE_DATE,
                java.util.List.of(MEMBERSHIP_ID),
                0,
                20
        )).willReturn(new AttendanceRecordQueryRepository.AttendanceRecordPage(
                java.util.List.of(record), 0, 20, 1L, 1
        ));

        var results = attendanceService.getDailyRecords(COHORT_ID, MANAGER_ID, ATTENDANCE_DATE, query);

        assertEquals(1, results.items().size());
        assertEquals(MEMBERSHIP_ID, results.items().getFirst().cohortMembershipId());
        assertEquals(1, results.totalElements());
    }

    @Test
    @DisplayName("내 출결은 날짜 범위와 페이지 조건으로 조회한다")
    void returnsMyRecordsByDateRangeAndPage() {
        AttendanceRecord record = AttendanceRecord.start(MEMBERSHIP_ID, ATTENDANCE_DATE);
        AttendancePageQuery query = AttendancePageQuery.of(
                ATTENDANCE_DATE.minusDays(7),
                ATTENDANCE_DATE,
                1,
                10
        );
        given(cohortAccessService.requireActiveMembershipId(COHORT_ID, USER_ID)).willReturn(MEMBERSHIP_ID);
        // 날짜 조건 분기와 정렬은 infrastructure 책임이므로 조회 조건 전달만 검증한다.
        given(attendanceRecordQueryRepository.findMemberRecords(
                MEMBERSHIP_ID,
                query.from(),
                query.to(),
                1,
                10
        )).willReturn(new AttendanceRecordQueryRepository.AttendanceRecordPage(
                java.util.List.of(record), 1, 10, 11L, 2
        ));

        var result = attendanceService.getMyRecords(COHORT_ID, USER_ID, query);

        assertEquals(1, result.items().size());
        assertEquals(1, result.page());
        assertEquals(11, result.totalElements());
        assertEquals(2, result.totalPages());
    }

    @Test
    @DisplayName("관리자는 자기 기수의 출결 상태만 변경")
    void changesFinalStatusOnlyForOwnCohortRecord() {
        Long attendanceId = 500L;
        AttendanceRecord record = AttendanceRecord.start(MEMBERSHIP_ID, ATTENDANCE_DATE);
        ReflectionTestUtils.setField(record, "id", attendanceId);
        given(attendanceRecordRepository.findById(attendanceId)).willReturn(Optional.of(record));
        given(cohortMembershipQueryService.findCohortIds(java.util.List.of(MEMBERSHIP_ID)))
                .willReturn(Map.of(MEMBERSHIP_ID, COHORT_ID));
        given(attendanceRecordRepository.save(record)).willReturn(record);

        var result = attendanceService.changeFinalStatus(
                COHORT_ID,
                attendanceId,
                MANAGER_ID,
                new ChangeAttendanceStatusCommand(
                        AttendanceStatus.PRESENT,
                        "관리자 확인",
                        "request-1"
                )
        );

        assertEquals(AttendanceStatus.PRESENT, result.finalStatus());
        verify(attendanceChangeLogRepository).save(any());
        verify(attendanceRecordRepository).save(record);
    }

    @Test
    @DisplayName("다른 기수의 출결 ID로 상태를 변경할 수 없음")
    void rejectsFinalStatusChangeForAnotherCohortRecord() {
        Long attendanceId = 500L;
        AttendanceRecord record = AttendanceRecord.start(MEMBERSHIP_ID, ATTENDANCE_DATE);
        ReflectionTestUtils.setField(record, "id", attendanceId);
        given(attendanceRecordRepository.findById(attendanceId)).willReturn(Optional.of(record));
        given(cohortMembershipQueryService.findCohortIds(java.util.List.of(MEMBERSHIP_ID)))
                .willReturn(Map.of(MEMBERSHIP_ID, 999L));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> attendanceService.changeFinalStatus(
                        COHORT_ID,
                        attendanceId,
                        MANAGER_ID,
                        new ChangeAttendanceStatusCommand(
                                AttendanceStatus.PRESENT,
                                "관리자 확인",
                                "request-2"
                        )
                )
        );

        assertSame(AttendanceErrorCode.ATTENDANCE_RECORD_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(attendanceChangeLogRepository);
    }

    private void givenActiveMembership() {
        given(cohortAccessService.requireActiveMembership(COHORT_ID, USER_ID))
                .willReturn(activeMembership());
        given(membershipRepository.findWithLockByIdAndStatus(MEMBERSHIP_ID, CohortMembershipStatus.ACTIVE))
                .willReturn(Optional.of(activeMembership()));
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
