package site.omagotchi.learningservice.cohort.application.dto.result;

import site.omagotchi.learningservice.cohort.domain.CohortAttendancePolicy;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 기수별 출결 정책 조회 결과
 */
public record CohortAttendancePolicyResponse(
        Long cohortId,
        String timezone,
        LocalTime scheduledStartTime,
        LocalTime scheduledEndTime,
        LocalTime absenceCutoffTime,
        Integer allowedAwayMinutes,
        UUID updatedByUserId,
        OffsetDateTime updatedAt
) {
    public static CohortAttendancePolicyResponse from(CohortAttendancePolicy policy) {
        return new CohortAttendancePolicyResponse(
                policy.getCohortId(),
                policy.getTimezone(),
                policy.getScheduledStartTime(),
                policy.getScheduledEndTime(),
                policy.getAbsenceCutoffTime(),
                policy.getAllowedAwayMinutes(),
                policy.getUpdatedByUserId(),
                policy.getUpdatedAt()
        );
    }
}
