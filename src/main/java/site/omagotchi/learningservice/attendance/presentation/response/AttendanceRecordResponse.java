package site.omagotchi.learningservice.attendance.presentation.response;

import site.omagotchi.learningservice.attendance.application.result.AttendanceRecordResult;
import site.omagotchi.learningservice.attendance.domain.AttendanceStatus;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 출결 기록 response
 */
public record AttendanceRecordResponse(
        Long id,
        LocalDate attendanceDate,
        AttendanceStatus autoStatus,
        AttendanceStatus finalStatus,
        Instant checkedInAt,
        Instant checkedOutAt,
        Integer lateMinutes,
        Integer earlyLeaveMinutes,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {

    public static AttendanceRecordResponse from(AttendanceRecordResult result) {
        return new AttendanceRecordResponse(
                result.id(),
                result.attendanceDate(),
                result.autoStatus(),
                result.finalStatus(),
                result.checkedInAt(),
                result.checkedOutAt(),
                result.lateMinutes(),
                result.earlyLeaveMinutes(),
                result.version(),
                result.createdAt(),
                result.updatedAt()
        );
    }
}
