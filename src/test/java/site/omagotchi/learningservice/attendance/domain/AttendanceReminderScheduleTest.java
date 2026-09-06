package site.omagotchi.learningservice.attendance.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("출결 알림 발화 시각")
class AttendanceReminderScheduleTest {

    @Test
    @DisplayName("예정 시각에서 여유 시간을 뺀 시각을 발화 시각으로 사용한다")
    void subtractsLeadTimeFromScheduledTime() {
        OffsetDateTime fireAt = AttendanceReminderSchedule.fireAt(
                OffsetDateTime.parse("2026-09-06T00:03:00+09:00"),
                Duration.ofMinutes(5)
        );

        assertThat(fireAt).isEqualTo(OffsetDateTime.parse("2026-09-05T23:58:00+09:00"));
    }

    @Test
    @DisplayName("음수 여유 시간은 거부한다")
    void rejectsNegativeLeadTime() {
        assertThatThrownBy(() -> AttendanceReminderSchedule.fireAt(
                OffsetDateTime.parse("2026-09-05T09:00:00+09:00"),
                Duration.ofMinutes(-1)
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
