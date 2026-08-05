package site.omagotchi.learningservice.attendance.domain;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import site.omagotchi.learningservice.cohort.domain.CohortAttendancePolicy;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class AttendanceDecisionPolicy {

    public static AttendanceDecision decideCheckIn(CohortAttendancePolicy policy, Instant checkedInAt) {
        ZoneId zoneId = ZoneId.of(policy.getTimezone());
        LocalTime checkedInTime = checkedInAt.atZone(zoneId).toLocalTime();
        int lateMinutes = calculateLateMinutes(policy, checkedInTime);
        return new AttendanceDecision(
                lateMinutes > 0 ? AttendanceStatus.LATE : AttendanceStatus.PENDING,
                lateMinutes,
                0
        );
    }

    public static AttendanceDecision decide(
            CohortAttendancePolicy policy,
            Instant checkedInAt,
            Instant checkedOutAt,
            List<PresenceInterval> intervals
    ) {
        if (checkedInAt == null || checkedOutAt == null) {
            return new AttendanceDecision(AttendanceStatus.ABSENT, 0, 0);
        }

        ZoneId zoneId = ZoneId.of(policy.getTimezone());
        LocalTime checkedInTime = checkedInAt.atZone(zoneId).toLocalTime();
        LocalTime checkedOutTime = checkedOutAt.atZone(zoneId).toLocalTime();
        int lateMinutes = calculateLateMinutes(policy, checkedInTime);
        int earlyLeaveMinutes = calculateEarlyLeaveMinutes(policy, checkedOutTime);

        if (hasInvalidAway(policy, zoneId, intervals)) {
            earlyLeaveMinutes = Math.max(earlyLeaveMinutes, 1);
        }

        return new AttendanceDecision(
                resolveStatus(lateMinutes, earlyLeaveMinutes),
                lateMinutes,
                earlyLeaveMinutes
        );
    }

    private static int calculateLateMinutes(CohortAttendancePolicy policy, LocalTime checkedInTime) {
        if (!checkedInTime.isAfter(policy.getScheduledStartTime())) {
            return 0;
        }
        return ceilToMinutes(Duration.between(policy.getScheduledStartTime(), checkedInTime));
    }

    private static int calculateEarlyLeaveMinutes(CohortAttendancePolicy policy, LocalTime checkedOutTime) {
        if (!checkedOutTime.isBefore(policy.getScheduledEndTime())) {
            return 0;
        }
        return ceilToMinutes(Duration.between(checkedOutTime, policy.getScheduledEndTime()));
    }

    private static boolean hasInvalidAway(
            CohortAttendancePolicy policy,
            ZoneId zoneId,
            List<PresenceInterval> intervals
    ) {
        if (intervals == null || intervals.isEmpty()) {
            return false;
        }
        return intervals.stream()
                .filter(interval -> interval.getState() == PresenceState.AWAY)
                .anyMatch(interval -> isInvalidAway(policy, zoneId, interval));
    }

    private static boolean isInvalidAway(
            CohortAttendancePolicy policy,
            ZoneId zoneId,
            PresenceInterval interval
    ) {
        if (interval.getEndedAt() == null) {
            return true;
        }
        LocalTime returnedAt = interval.getEndedAt().atZone(zoneId).toLocalTime();
        if (!returnedAt.isBefore(policy.getScheduledEndTime())) {
            return true;
        }
        int awayMinutes = ceilToMinutes(Duration.between(interval.getStartedAt(), interval.getEndedAt()));
        return awayMinutes > policy.getAllowedAwayMinutes();
    }

    private static int ceilToMinutes(Duration duration) {
        long seconds = duration.getSeconds();
        if (duration.getNano() > 0) {
            seconds++;
        }
        return Math.toIntExact((seconds + 59) / 60);
    }

    private static AttendanceStatus resolveStatus(int lateMinutes, int earlyLeaveMinutes) {
        if (lateMinutes > 0 && earlyLeaveMinutes > 0) {
            return AttendanceStatus.LATE_LEFT_EARLY;
        }
        if (lateMinutes > 0) {
            return AttendanceStatus.LATE;
        }
        if (earlyLeaveMinutes > 0) {
            return AttendanceStatus.LEFT_EARLY;
        }
        return AttendanceStatus.PRESENT;
    }
}
