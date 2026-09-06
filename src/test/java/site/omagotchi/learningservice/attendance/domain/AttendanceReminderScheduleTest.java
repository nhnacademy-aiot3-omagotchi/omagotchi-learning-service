package site.omagotchi.learningservice.attendance.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.omagotchi.learningservice.cohort.domain.CohortAttendancePolicy;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("출결 알림 발화 시각")
class AttendanceReminderScheduleTest {

    private static final UUID ADMIN_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    @DisplayName("04시 이전 정책 시간은 집계일의 다음 날에 배치한 뒤 여유 시간을 뺀다")
    void subtractsLeadTimeAfterApplyingDateAndZone() {
        CohortAttendancePolicy policy = policy(LocalTime.of(0, 3), LocalTime.of(3, 0));

        OffsetDateTime fireAt = AttendanceReminderSchedule.fireAt(
                LocalDate.of(2026, 9, 5),
                ReminderType.CHECK_IN_BEFORE_DEADLINE,
                policy,
                Duration.ofMinutes(5)
        );

        assertThat(fireAt).isEqualTo(OffsetDateTime.parse("2026-09-05T23:58:00+09:00"));
    }

    @Test
    @DisplayName("입실과 퇴실 알림은 각 정책 시각에서 유도한다")
    void derivesEachReminderFromItsScheduledTime() {
        CohortAttendancePolicy policy = policy(LocalTime.of(9, 0), LocalTime.of(18, 0));
        LocalDate attendanceDate = LocalDate.of(2026, 9, 5);

        assertThat(AttendanceReminderSchedule.fireAt(
                attendanceDate,
                ReminderType.CHECK_IN_BEFORE_DEADLINE,
                policy,
                Duration.ofMinutes(5)
        )).isEqualTo(OffsetDateTime.parse("2026-09-05T08:55:00+09:00"));
        assertThat(AttendanceReminderSchedule.fireAt(
                attendanceDate,
                ReminderType.CHECK_OUT_BEFORE_DEADLINE,
                policy,
                Duration.ofMinutes(5)
        )).isEqualTo(OffsetDateTime.parse("2026-09-05T17:55:00+09:00"));
    }

    private CohortAttendancePolicy policy(LocalTime startTime, LocalTime endTime) {
        return CohortAttendancePolicy.create(
                1L,
                "Asia/Seoul",
                startTime,
                endTime,
                null,
                30,
                ADMIN_ID
        );
    }
}
