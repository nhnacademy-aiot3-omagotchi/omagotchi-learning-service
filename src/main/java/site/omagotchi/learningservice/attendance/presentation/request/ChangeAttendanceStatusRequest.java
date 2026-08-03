package site.omagotchi.learningservice.attendance.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import site.omagotchi.learningservice.attendance.application.command.ChangeAttendanceStatusCommand;
import site.omagotchi.learningservice.attendance.domain.AttendanceStatus;

/**
 * 출결 상태 변경 요청
 * 현재 상태 -> 다음 (변경될) 상태
 * 사유
 * 요청 ID
 */
public record ChangeAttendanceStatusRequest(
        @NotNull AttendanceStatus nextStatus,
        @NotBlank String reason,
        @NotBlank String requestId
) {

    public ChangeAttendanceStatusCommand toCommand() {
        return new ChangeAttendanceStatusCommand(nextStatus, reason, requestId);
    }
}
