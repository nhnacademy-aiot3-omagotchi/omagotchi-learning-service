package site.omagotchi.learningservice.attendance.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("출결 기록")
class AttendanceRecordTest {

    private static final Instant CHECKED_IN_AT = Instant.parse("2026-09-04T00:00:00Z");

    @Test
    @DisplayName("체크인했지만 퇴실하지 않은 출결을 미퇴실 상태로 확정한다")
    void marksCheckedInAttendanceAsMissingCheckOut() {
        AttendanceRecord record = checkedInAttendance();

        assertThat(record.markMissingCheckOut()).isTrue();

        assertThat(record.getAutoStatus()).isEqualTo(AttendanceStatus.MISSING_CHECK_OUT);
        assertThat(record.getFinalStatus()).isEqualTo(AttendanceStatus.MISSING_CHECK_OUT);
        assertThat(record.getCheckedOutAt()).isNull();
    }

    @Test
    @DisplayName("체크인하지 않은 출결은 미퇴실로 바꾸지 않는다")
    void leavesNotCheckedInAttendanceUntouched() {
        AttendanceRecord record = AttendanceRecord.start(1L, LocalDate.of(2026, 9, 4));

        assertThat(record.markMissingCheckOut()).isFalse();
        assertThat(record.getAutoStatus()).isEqualTo(AttendanceStatus.PENDING);
        assertThat(record.getFinalStatus()).isEqualTo(AttendanceStatus.PENDING);
    }

    @Test
    @DisplayName("정상 퇴실한 출결은 미퇴실로 바꾸지 않는다")
    void leavesCheckedOutAttendanceUntouched() {
        AttendanceRecord record = checkedInAttendance();
        record.checkOut(
                Instant.parse("2026-09-04T09:00:00Z"),
                AttendanceStatus.PRESENT,
                0
        );

        assertThat(record.markMissingCheckOut()).isFalse();
        assertThat(record.getAutoStatus()).isEqualTo(AttendanceStatus.PRESENT);
        assertThat(record.getFinalStatus()).isEqualTo(AttendanceStatus.PRESENT);
    }

    @Test
    @DisplayName("이미 미퇴실 판정된 출결의 관리자 최종 상태를 재실행이 덮어쓰지 않는다")
    void repeatedMarkPreservesManagerOverride() {
        AttendanceRecord record = checkedInAttendance();
        record.markMissingCheckOut();
        record.overrideFinalStatus(AttendanceStatus.PRESENT);

        assertThat(record.markMissingCheckOut()).isFalse();
        assertThat(record.getAutoStatus()).isEqualTo(AttendanceStatus.MISSING_CHECK_OUT);
        assertThat(record.getFinalStatus()).isEqualTo(AttendanceStatus.PRESENT);
    }

    private AttendanceRecord checkedInAttendance() {
        AttendanceRecord record = AttendanceRecord.start(1L, LocalDate.of(2026, 9, 4));
        record.checkIn(CHECKED_IN_AT, AttendanceStatus.PRESENT, 0);
        return record;
    }
}
