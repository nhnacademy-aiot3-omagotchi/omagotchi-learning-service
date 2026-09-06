package site.omagotchi.learningservice.attendance.domain;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;

/** 예정 시각에서 알림 발화 시각을 유도하는 순수 함수. */
public final class AttendanceReminderSchedule {

    private AttendanceReminderSchedule() {
    }

    public static OffsetDateTime fireAt(
            OffsetDateTime scheduledAt,
            Duration leadTime
    ) {
        Objects.requireNonNull(scheduledAt, "scheduledAt은 필수입니다.");
        Objects.requireNonNull(leadTime, "leadTime은 필수입니다.");
        if (leadTime.isNegative()) {
            throw new IllegalArgumentException("leadTime은 음수일 수 없습니다.");
        }
        return scheduledAt.minus(leadTime);
    }
}
