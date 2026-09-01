package site.omagotchi.learningservice.attendance.presentation.response;

import site.omagotchi.learningservice.attendance.application.result.AttendanceRecordResult;
import site.omagotchi.learningservice.attendance.domain.AttendanceStatus;

import java.time.Instant;
import java.time.LocalDate;

/** 실습실 최초 선택·이동 결과. 출결 정보와 확정된 공간을 함께 반환한다. */
public record AttendanceLabMoveResponse(
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

    public static AttendanceLabMoveResponse from(
            AttendanceRecordResult result,
            Long spaceId
    ) {
        return new AttendanceLabMoveResponse(
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
