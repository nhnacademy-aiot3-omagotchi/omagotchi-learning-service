package site.omagotchi.learningservice.attendance.domain;

import site.omagotchi.learningservice.cohort.domain.CohortAttendancePolicy;
import site.omagotchi.learningservice.global.time.AggregationDateTime;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Objects;

/** 출결 정책에서 알림 발화 시각을 유도하는 순수 함수. */
public final class AttendanceReminderSchedule {

    private AttendanceReminderSchedule() {
    }

    public static OffsetDateTime fireAt(
            LocalDate attendanceDate,
            ReminderType reminderType,
            CohortAttendancePolicy policy,
            Duration leadTime
    ) {
        Objects.requireNonNull(attendanceDate, "attendanceDate는 필수입니다.");
        Objects.requireNonNull(reminderType, "reminderType은 필수입니다.");
        Objects.requireNonNull(policy, "attendancePolicy는 필수입니다.");
        Objects.requireNonNull(leadTime, "leadTime은 필수입니다.");
        if (leadTime.isNegative()) {
            throw new IllegalArgumentException("leadTime은 음수일 수 없습니다.");
        }

        return scheduledAt(attendanceDate, reminderType, policy).minus(leadTime);
    }

    public static OffsetDateTime scheduledAt(
            LocalDate attendanceDate,
            ReminderType reminderType,
            CohortAttendancePolicy policy
    ) {
        Objects.requireNonNull(attendanceDate, "attendanceDate는 필수입니다.");
        Objects.requireNonNull(reminderType, "reminderType은 필수입니다.");
        Objects.requireNonNull(policy, "attendancePolicy는 필수입니다.");

        return AggregationDateTime.dateTimeWithin(
                        attendanceDate,
                        scheduledTimeOf(reminderType, policy),
                        ZoneId.of(policy.getTimezone())
                )
                .toOffsetDateTime();
    }

    private static LocalTime scheduledTimeOf(
            ReminderType reminderType,
            CohortAttendancePolicy policy
    ) {
        return switch (reminderType) {
            case CHECK_IN_BEFORE_DEADLINE -> policy.getScheduledStartTime();
            case CHECK_OUT_BEFORE_DEADLINE -> policy.getScheduledEndTime();
        };
    }
}
