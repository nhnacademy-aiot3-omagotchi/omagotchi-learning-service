package site.omagotchi.learningservice.attendance.application.command;

import site.omagotchi.learningservice.attendance.domain.AttendanceStatus;

/**
 * 출석 상태, 사유, 요청 아이디
 */
public record ChangeAttendanceStatusCommand(
        AttendanceStatus nextStatus,
        String reason,
        String requestId
) {
}
