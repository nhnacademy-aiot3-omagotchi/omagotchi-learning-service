package site.omagotchi.learningservice.attendance.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("출결 알림 이력")
class AttendanceReminderTest {

    private static final OffsetDateTime STARTED_AT =
            OffsetDateTime.parse("2026-09-05T00:00:00Z");

    @Test
    @DisplayName("발송 시작 전에는 PENDING 상태로 기록한다")
    void startsPending() {
        AttendanceReminder reminder = reminder();

        assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.PENDING);
        assertThat(reminder.getSentAt()).isNull();
        assertThat(reminder.getAttemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("현재 시도의 성공 완료만 SENT로 전이한다")
    void marksOnlyCurrentAttemptAsSent() {
        AttendanceReminder reminder = reminder();
        reminder.markFailed(1, "first failure", STARTED_AT.plusSeconds(10));
        reminder.retry(STARTED_AT.plusMinutes(1));

        boolean staleCompletion = reminder.markSent(1, STARTED_AT.plusMinutes(1));
        boolean currentCompletion = reminder.markSent(2, STARTED_AT.plusMinutes(1));

        assertThat(staleCompletion).isFalse();
        assertThat(currentCompletion).isTrue();
        assertThat(reminder.getStatus()).isEqualTo(ReminderStatus.SENT);
        assertThat(reminder.getAttemptCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("실패와 건너뜀은 재시도할 수 있지만 SENT는 재시도하지 않는다")
    void allowsRetryByStatus() {
        AttendanceReminder failed = reminder();
        failed.markFailed(1, "failure", STARTED_AT.plusSeconds(10));
        AttendanceReminder skipped = reminder();
        skipped.markSkipped(1, "disabled", STARTED_AT.plusSeconds(10));
        AttendanceReminder sent = reminder();
        sent.markSent(1, STARTED_AT.plusSeconds(10));

        assertThat(failed.canRetry(STARTED_AT)).isTrue();
        assertThat(skipped.canRetry(STARTED_AT)).isTrue();
        assertThat(sent.canRetry(STARTED_AT)).isFalse();
    }

    @Test
    @DisplayName("제한 시간을 넘긴 PENDING만 재시도할 수 있다")
    void retriesOnlyStalePending() {
        AttendanceReminder reminder = reminder();

        assertThat(reminder.canRetry(STARTED_AT.minusSeconds(1))).isFalse();
        assertThat(reminder.canRetry(STARTED_AT)).isTrue();
    }

    private AttendanceReminder reminder() {
        return AttendanceReminder.pending(
                10L,
                LocalDate.of(2026, 9, 5),
                ReminderType.CHECK_IN_BEFORE_DEADLINE,
                ReminderChannel.TELEGRAM,
                STARTED_AT
        );
    }
}
