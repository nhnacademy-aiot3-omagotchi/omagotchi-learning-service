package site.omagotchi.learningservice.attendance.application.result;

import java.time.LocalDate;

/** 일일 미퇴실 마감 후보. */
public record DailyMissingCheckOutTarget(
        Long attendanceId,
        Long cohortMembershipId,
        LocalDate attendanceDate
) {
}
