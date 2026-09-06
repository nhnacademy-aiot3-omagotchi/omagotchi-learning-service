package site.omagotchi.learningservice.attendance.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.attendance.application.port.AttendanceReminderPersistence;
import site.omagotchi.learningservice.attendance.application.result.AttendanceReminderAttempt;
import site.omagotchi.learningservice.attendance.domain.AttendanceReminder;
import site.omagotchi.learningservice.attendance.domain.ReminderChannel;
import site.omagotchi.learningservice.attendance.domain.ReminderStatus;
import site.omagotchi.learningservice.attendance.domain.ReminderType;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("출결 알림 발송 시도 서비스")
class AttendanceReminderAttemptServiceTest {

    private static final Long MEMBERSHIP_ID = 10L;
    private static final LocalDate ATTENDANCE_DATE = LocalDate.of(2026, 9, 5);
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-05T00:00:00Z");

    @Mock
    private AttendanceReminderPersistence persistence;

    @Test
    @DisplayName("처음 발송하는 알림은 PENDING 이력을 삽입하고 1번 시도를 획득한다")
    void startsFirstAttemptAsPending() {
        given(persistence.insertIfAbsent(any())).willReturn(true);

        var attempt = service().start(
                MEMBERSHIP_ID,
                ATTENDANCE_DATE,
                ReminderType.CHECK_IN_BEFORE_DEADLINE,
                ReminderChannel.TELEGRAM
        );

        ArgumentCaptor<AttendanceReminder> pending =
                ArgumentCaptor.forClass(AttendanceReminder.class);
        verify(persistence).insertIfAbsent(pending.capture());
        assertThat(attempt).contains(new AttendanceReminderAttempt(
                MEMBERSHIP_ID,
                ATTENDANCE_DATE,
                ReminderType.CHECK_IN_BEFORE_DEADLINE,
                ReminderChannel.TELEGRAM,
                1
        ));
        assertThat(pending.getValue().getStatus()).isEqualTo(ReminderStatus.PENDING);
        assertThat(pending.getValue().getSentAt()).isNull();
    }

    @Test
    @DisplayName("FAILED 이력은 PENDING으로 되돌리고 시도 번호를 증가시킨다")
    void retriesFailedAttempt() {
        AttendanceReminder failed = reminder(NOW.minusMinutes(2));
        failed.markFailed(1, "telegram unavailable", NOW.minusMinutes(1));
        given(persistence.insertIfAbsent(any())).willReturn(false);
        given(persistence.findForUpdate(
                MEMBERSHIP_ID,
                ATTENDANCE_DATE,
                ReminderType.CHECK_IN_BEFORE_DEADLINE,
                ReminderChannel.TELEGRAM
        )).willReturn(Optional.of(failed));

        var attempt = service().start(
                MEMBERSHIP_ID,
                ATTENDANCE_DATE,
                ReminderType.CHECK_IN_BEFORE_DEADLINE,
                ReminderChannel.TELEGRAM
        );

        assertThat(attempt).get().extracting(AttendanceReminderAttempt::attemptCount)
                .isEqualTo(2);
        assertThat(failed.getStatus()).isEqualTo(ReminderStatus.PENDING);
        assertThat(failed.getLastError()).isNull();
    }

    @Test
    @DisplayName("제한 시간 안의 PENDING 이력은 동시에 다시 획득하지 않는다")
    void doesNotRetryFreshPendingAttempt() {
        AttendanceReminder pending = reminder(NOW.minusSeconds(30));
        given(persistence.insertIfAbsent(any())).willReturn(false);
        given(persistence.findForUpdate(
                MEMBERSHIP_ID,
                ATTENDANCE_DATE,
                ReminderType.CHECK_IN_BEFORE_DEADLINE,
                ReminderChannel.TELEGRAM
        )).willReturn(Optional.of(pending));

        var attempt = service().start(
                MEMBERSHIP_ID,
                ATTENDANCE_DATE,
                ReminderType.CHECK_IN_BEFORE_DEADLINE,
                ReminderChannel.TELEGRAM
        );

        assertThat(attempt).isEmpty();
        assertThat(pending.getAttemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("성공 완료는 현재 PENDING 시도를 SENT로 확정한다")
    void marksCurrentAttemptAsSent() {
        AttendanceReminder pending = reminder(NOW.minusSeconds(10));
        AttendanceReminderAttempt attempt = AttendanceReminderAttempt.from(pending);
        given(persistence.findForUpdate(
                MEMBERSHIP_ID,
                ATTENDANCE_DATE,
                ReminderType.CHECK_IN_BEFORE_DEADLINE,
                ReminderChannel.TELEGRAM
        )).willReturn(Optional.of(pending));

        service().markSent(attempt);

        assertThat(pending.getStatus()).isEqualTo(ReminderStatus.SENT);
        assertThat(pending.getSentAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("false 완료는 현재 PENDING 시도를 SKIPPED로 확정한다")
    void marksCurrentAttemptAsSkipped() {
        AttendanceReminder pending = reminder(NOW.minusSeconds(10));
        AttendanceReminderAttempt attempt = AttendanceReminderAttempt.from(pending);
        given(persistence.findForUpdate(
                MEMBERSHIP_ID,
                ATTENDANCE_DATE,
                ReminderType.CHECK_IN_BEFORE_DEADLINE,
                ReminderChannel.TELEGRAM
        )).willReturn(Optional.of(pending));

        service().markSkipped(attempt, "notifications disabled");

        assertThat(pending.getStatus()).isEqualTo(ReminderStatus.SKIPPED);
        assertThat(pending.getLastError()).isEqualTo("notifications disabled");
        assertThat(pending.getSentAt()).isNull();
    }

    private AttendanceReminderAttemptService service() {
        return new AttendanceReminderAttemptService(
                persistence,
                Clock.fixed(Instant.from(NOW), ZoneOffset.UTC)
        );
    }

    private AttendanceReminder reminder(OffsetDateTime startedAt) {
        return AttendanceReminder.pending(
                MEMBERSHIP_ID,
                ATTENDANCE_DATE,
                ReminderType.CHECK_IN_BEFORE_DEADLINE,
                ReminderChannel.TELEGRAM,
                startedAt
        );
    }
}
