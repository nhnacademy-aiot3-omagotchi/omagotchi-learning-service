package site.omagotchi.learningservice.attendance.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.attendance.domain.AttendanceRecord;
import site.omagotchi.learningservice.attendance.domain.AttendanceStatus;
import site.omagotchi.learningservice.attendance.infrastructure.AttendanceRecordRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("미퇴실 출결 단건 마감")
class MissingCheckOutFinalizerTest {

    private static final Long ATTENDANCE_ID = 10L;
    private static final Instant ENDED_AT = Instant.parse("2026-09-04T09:00:00Z");

    @Mock
    private PresenceTransitionService presenceTransitionService;

    @Mock
    private AttendanceRecordRepository attendanceRecordRepository;

    @InjectMocks
    private MissingCheckOutFinalizer finalizer;

    @Test
    @DisplayName("체류 구간과 출결 상태를 함께 마감한다")
    void closesPresenceAndFinalizesAttendance() {
        AttendanceRecord attendance = checkedInAttendance();
        when(presenceTransitionService.closeAttendance(ATTENDANCE_ID, ENDED_AT))
                .thenReturn(true);
        when(attendanceRecordRepository.findById(ATTENDANCE_ID))
                .thenReturn(Optional.of(attendance));

        assertThat(finalizer.finalizeOne(ATTENDANCE_ID, ENDED_AT)).isTrue();

        assertThat(attendance.getAutoStatus()).isEqualTo(AttendanceStatus.MISSING_CHECK_OUT);
        assertThat(attendance.getFinalStatus()).isEqualTo(AttendanceStatus.MISSING_CHECK_OUT);
        assertThat(attendance.getCheckedOutAt()).isNull();
    }

    @Test
    @DisplayName("열린 체류 구간이 없어도 미해결 출결 상태는 마감한다")
    void finalizesAttendanceWithoutOpenPresence() {
        AttendanceRecord attendance = checkedInAttendance();
        when(presenceTransitionService.closeAttendance(ATTENDANCE_ID, ENDED_AT))
                .thenReturn(false);
        when(attendanceRecordRepository.findById(ATTENDANCE_ID))
                .thenReturn(Optional.of(attendance));

        assertThat(finalizer.finalizeOne(ATTENDANCE_ID, ENDED_AT)).isTrue();
        assertThat(attendance.getAutoStatus()).isEqualTo(AttendanceStatus.MISSING_CHECK_OUT);
    }

    @Test
    @DisplayName("이미 미퇴실 판정된 출결은 열린 체류 구간만 마감하고 관리자 교정을 보존한다")
    void closesOrphanPresenceWithoutOverwritingManagerOverride() {
        AttendanceRecord attendance = checkedInAttendance();
        attendance.markMissingCheckOut();
        attendance.overrideFinalStatus(AttendanceStatus.PRESENT);
        when(presenceTransitionService.closeAttendance(ATTENDANCE_ID, ENDED_AT))
                .thenReturn(true);
        when(attendanceRecordRepository.findById(ATTENDANCE_ID))
                .thenReturn(Optional.of(attendance));

        assertThat(finalizer.finalizeOne(ATTENDANCE_ID, ENDED_AT)).isTrue();
        assertThat(attendance.getAutoStatus()).isEqualTo(AttendanceStatus.MISSING_CHECK_OUT);
        assertThat(attendance.getFinalStatus()).isEqualTo(AttendanceStatus.PRESENT);
    }

    @Test
    @DisplayName("체류 마감이 실패하면 출결 상태를 변경하지 않는다")
    void doesNotFinalizeAttendanceWhenPresenceCloseFails() {
        AttendanceRecord attendance = checkedInAttendance();
        when(presenceTransitionService.closeAttendance(ATTENDANCE_ID, ENDED_AT))
                .thenThrow(new IllegalStateException("체류 마감 실패"));

        assertThatThrownBy(() -> finalizer.finalizeOne(ATTENDANCE_ID, ENDED_AT))
                .isInstanceOf(IllegalStateException.class);

        verify(attendanceRecordRepository, never()).findById(ATTENDANCE_ID);
        assertThat(attendance.getAutoStatus()).isEqualTo(AttendanceStatus.PRESENT);
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
