package site.omagotchi.learningservice.cohort.application.dto.command;

import java.time.LocalTime;

/**
 * 기수별 출결 정책 저장 명령
 */
public record SaveAttendancePolicyCommand(
        String timezone,
        LocalTime scheduledStartTime,
        LocalTime scheduledEndTime,
        LocalTime absenceCutoffTime,
        Integer allowedAwayMinutes
) {
}
