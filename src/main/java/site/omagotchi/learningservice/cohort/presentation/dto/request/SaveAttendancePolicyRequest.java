package site.omagotchi.learningservice.cohort.presentation.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import site.omagotchi.learningservice.cohort.application.command.SaveAttendancePolicyCommand;

import java.time.LocalTime;

/**
 * 기수별 출결 정책 저장 요청
 */
public record SaveAttendancePolicyRequest(
        @NotBlank String timezone,
        @NotNull LocalTime scheduledStartTime,
        @NotNull LocalTime scheduledEndTime,
        LocalTime absenceCutoffTime,
        @NotNull @Min(0) Integer allowedAwayMinutes
) {

    public SaveAttendancePolicyCommand toCommand() {
        return new SaveAttendancePolicyCommand(
                timezone,
                scheduledStartTime,
                scheduledEndTime,
                absenceCutoffTime,
                allowedAwayMinutes
        );
    }
}
