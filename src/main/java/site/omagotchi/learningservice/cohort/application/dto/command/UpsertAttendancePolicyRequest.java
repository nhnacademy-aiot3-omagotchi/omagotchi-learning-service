package site.omagotchi.learningservice.cohort.application.dto.command;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

/**
 * 기수별 출결 정책 생성 또는 수정 요청
 */
public record UpsertAttendancePolicyRequest(
        @NotBlank String timezone,
        @NotNull LocalTime scheduledStartTime,
        @NotNull LocalTime scheduledEndTime,
        LocalTime absenceCutoffTime,
        @NotNull @Min(0) Integer allowedAwayMinutes
) {
}
