package site.omagotchi.learningservice.attendance.presentation.response;

import site.omagotchi.learningservice.attendance.application.result.AttendanceRecordResult;
import site.omagotchi.learningservice.attendance.domain.AttendanceStatus;

import java.time.Instant;
import java.time.LocalDate;

/** 현재 출결의 재실 공간 이동 결과. */
public record AttendanceSpaceMoveResponse(
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
        Instant updatedAt,
        Long spaceId
) {

    public static AttendanceSpaceMoveResponse from(
            AttendanceRecordResult result,
            Long spaceId
    ) {
        return new AttendanceSpaceMoveResponse(
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
                result.updatedAt(),
                spaceId
        );
    }
}
