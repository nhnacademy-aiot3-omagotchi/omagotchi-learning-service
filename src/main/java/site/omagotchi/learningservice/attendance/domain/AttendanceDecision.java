package site.omagotchi.learningservice.attendance.domain;

public record AttendanceDecision(
        AttendanceStatus status,
        int lateMinutes,
        int earlyLeaveMinutes
) {
}
