package site.omagotchi.learningservice.attendance.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.attendance.application.result.AttendanceCloseResult;
import site.omagotchi.learningservice.attendance.domain.AttendanceRecord;
import site.omagotchi.learningservice.attendance.domain.AttendanceStatus;
import site.omagotchi.learningservice.attendance.infrastructure.AttendanceRecordRepository;
import site.omagotchi.learningservice.cohort.application.CohortLockService;
import site.omagotchi.learningservice.cohort.application.result.CohortMembershipView;
import site.omagotchi.learningservice.cohort.application.result.EndedMembershipLockView;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("미퇴실 출결 단건 마감")
class MissingCheckOutFinalizerTest {

    private static final Long ATTENDANCE_ID = 10L;
    private static final Long MEMBERSHIP_ID = 20L;
    private static final OffsetDateTime MEMBERSHIP_ENDED_AT =
            OffsetDateTime.parse("2026-09-04T09:00:00Z");
    private static final Instant ENDED_AT = MEMBERSHIP_ENDED_AT.toInstant();

    @Mock
    private PresenceTransitionService presenceTransitionService;

    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;

    @Mock
    private CohortLockService cohortLockService;

    @InjectMocks
    private MissingCheckOutFinalizer finalizer;

    @BeforeEach
    void stubMembershipLock() {
        lenient().when(cohortLockService.lockEndedMembership(MEMBERSHIP_ID))
                .thenReturn(Optional.of(new EndedMembershipLockView(
                        MEMBERSHIP_ID,
                        MEMBERSHIP_ENDED_AT
                )));
    }

    @Test
    @DisplayName("체류 구간과 출결 상태를 함께 마감한다")
    void closesPresenceAndFinalizesAttendance() {
        AttendanceRecord attendance = checkedInAttendance();
        when(presenceTransitionService.closeAttendanceUnlessMeeting(
                ATTENDANCE_ID,
                MEMBERSHIP_ID,
                ENDED_AT
        ))
                .thenReturn(AttendanceCloseResult.CLOSED);
        when(attendanceRecordRepository.findById(ATTENDANCE_ID))
                .thenReturn(Optional.of(attendance));

        assertThat(finalizer.finalizeOne(ATTENDANCE_ID, MEMBERSHIP_ID)).isTrue();

        assertThat(attendance.getAutoStatus()).isEqualTo(AttendanceStatus.MISSING_CHECK_OUT);
        assertThat(attendance.getFinalStatus()).isEqualTo(AttendanceStatus.MISSING_CHECK_OUT);
        assertThat(attendance.getCheckedOutAt()).isNull();
    }

    @Test
    @DisplayName("열린 체류 구간이 없어도 미해결 출결 상태는 마감한다")
    void finalizesAttendanceWithoutOpenPresence() {
        AttendanceRecord attendance = checkedInAttendance();
        when(presenceTransitionService.closeAttendanceUnlessMeeting(
                ATTENDANCE_ID,
                MEMBERSHIP_ID,
                ENDED_AT
        ))
                .thenReturn(AttendanceCloseResult.ALREADY_CLOSED);
        when(attendanceRecordRepository.findById(ATTENDANCE_ID))
                .thenReturn(Optional.of(attendance));

        assertThat(finalizer.finalizeOne(ATTENDANCE_ID, MEMBERSHIP_ID)).isTrue();
        assertThat(attendance.getAutoStatus()).isEqualTo(AttendanceStatus.MISSING_CHECK_OUT);
    }

    @Test
    @DisplayName("이미 미퇴실 판정된 출결은 열린 체류 구간만 마감하고 관리자 교정을 보존한다")
    void closesOrphanPresenceWithoutOverwritingManagerOverride() {
        AttendanceRecord attendance = checkedInAttendance();
        attendance.markMissingCheckOut();
        attendance.overrideFinalStatus(AttendanceStatus.PRESENT);
        when(presenceTransitionService.closeAttendanceUnlessMeeting(
                ATTENDANCE_ID,
                MEMBERSHIP_ID,
                ENDED_AT
        ))
                .thenReturn(AttendanceCloseResult.CLOSED);
        when(attendanceRecordRepository.findById(ATTENDANCE_ID))
                .thenReturn(Optional.of(attendance));

        assertThat(finalizer.finalizeOne(ATTENDANCE_ID, MEMBERSHIP_ID)).isTrue();
        assertThat(attendance.getAutoStatus()).isEqualTo(AttendanceStatus.MISSING_CHECK_OUT);
        assertThat(attendance.getFinalStatus()).isEqualTo(AttendanceStatus.PRESENT);
    }

    @Test
    @DisplayName("체류 마감이 실패하면 출결 상태를 변경하지 않는다")
    void doesNotFinalizeAttendanceWhenPresenceCloseFails() {
        AttendanceRecord attendance = checkedInAttendance();
        when(presenceTransitionService.closeAttendanceUnlessMeeting(
                ATTENDANCE_ID,
                MEMBERSHIP_ID,
                ENDED_AT
        ))
                .thenThrow(new IllegalStateException("체류 마감 실패"));

        assertThatThrownBy(() -> finalizer.finalizeOne(
                ATTENDANCE_ID,
                MEMBERSHIP_ID
        ))
                .isInstanceOf(IllegalStateException.class);

        verify(attendanceRecordRepository, never()).findById(ATTENDANCE_ID);
        assertThat(attendance.getAutoStatus()).isEqualTo(AttendanceStatus.PRESENT);
    }

    @Test
    @DisplayName("잠금 뒤 열린 회의 구간을 발견하면 출결을 미퇴실로 확정하지 않는다")
    void skipsFinalizationWhileMeetingIsOpen() {
        when(presenceTransitionService.closeAttendanceUnlessMeeting(
                ATTENDANCE_ID,
                MEMBERSHIP_ID,
                ENDED_AT
        ))
                .thenReturn(AttendanceCloseResult.MEETING_OPEN);

        assertThat(finalizer.finalizeOne(
                ATTENDANCE_ID,
                MEMBERSHIP_ID
        )).isFalse();

        verify(attendanceRecordRepository, never()).findById(ATTENDANCE_ID);
    }

    @Test
    @DisplayName("소속이 종료 상태가 아니면 체류와 출결을 건드리지 않는다")
    void skipsFinalizationWithoutEndedMembershipLock() {
        when(cohortLockService.lockEndedMembership(MEMBERSHIP_ID))
                .thenReturn(Optional.empty());

        assertThat(finalizer.finalizeOne(
                ATTENDANCE_ID,
                MEMBERSHIP_ID
        )).isFalse();

        verifyNoInteractions(presenceTransitionService, attendanceRecordRepository);
    }

    @Test
    @DisplayName("일일 마감은 ACTIVE 소속을 잠그고 정책 마감 시각을 사용한다")
    void finalizesDailyMissingCheckOutForActiveMembership() {
        AttendanceRecord attendance = checkedInAttendance();
        when(cohortLockService.lockActiveMembership(MEMBERSHIP_ID))
                .thenReturn(Optional.of(new CohortMembershipView(
                        MEMBERSHIP_ID,
                        1L,
                        UUID.randomUUID()
                )));
        when(presenceTransitionService.closeAttendanceAtDailyDeadline(
                ATTENDANCE_ID,
                MEMBERSHIP_ID,
                ENDED_AT
        )).thenReturn(AttendanceCloseResult.CLOSED);
        when(attendanceRecordRepository.findById(ATTENDANCE_ID))
                .thenReturn(Optional.of(attendance));

        assertThat(finalizer.finalizeDaily(
                ATTENDANCE_ID,
                MEMBERSHIP_ID,
                ENDED_AT
        )).isTrue();

        assertThat(attendance.getAutoStatus())
                .isEqualTo(AttendanceStatus.MISSING_CHECK_OUT);
        assertThat(attendance.getCheckedOutAt()).isNull();
    }

    @Test
    @DisplayName("일일 마감 직전 소속이 종료됐으면 종료 전용 정리에 맡긴다")
    void skipsDailyFinalizationWhenMembershipIsNoLongerActive() {
        when(cohortLockService.lockActiveMembership(MEMBERSHIP_ID))
                .thenReturn(Optional.empty());

        assertThat(finalizer.finalizeDaily(
                ATTENDANCE_ID,
                MEMBERSHIP_ID,
                ENDED_AT
        )).isFalse();

        verifyNoInteractions(presenceTransitionService, attendanceRecordRepository);
    }

    private AttendanceRecord checkedInAttendance() {
        AttendanceRecord attendance = AttendanceRecord.start(
                1L,
                LocalDate.of(2026, 9, 4)
        );
        attendance.checkIn(
                Instant.parse("2026-09-04T00:00:00Z"),
                AttendanceStatus.PRESENT,
                0
        );
        return attendance;
    }
}
