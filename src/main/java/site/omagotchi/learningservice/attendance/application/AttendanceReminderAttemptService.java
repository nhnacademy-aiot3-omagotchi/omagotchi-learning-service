package site.omagotchi.learningservice.attendance.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.attendance.application.port.AttendanceReminderPersistence;
import site.omagotchi.learningservice.attendance.application.result.AttendanceReminderAttempt;
import site.omagotchi.learningservice.attendance.domain.AttendanceReminder;
import site.omagotchi.learningservice.attendance.domain.ReminderChannel;
import site.omagotchi.learningservice.attendance.domain.ReminderType;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

/** 출결 알림 발송 시도의 획득과 완료 상태 전이를 각각 짧은 트랜잭션으로 처리한다. */
@Service
@RequiredArgsConstructor
public class AttendanceReminderAttemptService {

    static final Duration PENDING_TIMEOUT = Duration.ofMinutes(1);

    private final AttendanceReminderPersistence persistence;
    private final Clock clock;

    /** 신규 이력을 만들거나 재시도 가능한 기존 이력을 PENDING으로 전이한다. */
    @Transactional
    public Optional<AttendanceReminderAttempt> start(
            Long cohortMembershipId,
            LocalDate attendanceDate,
            ReminderType reminderType,
            ReminderChannel channel
    ) {
        OffsetDateTime startedAt = OffsetDateTime.now(clock);
        AttendanceReminder pending = AttendanceReminder.pending(
                cohortMembershipId,
                attendanceDate,
                reminderType,
                channel,
                startedAt
        );
        if (persistence.insertIfAbsent(pending)) {
            return Optional.of(AttendanceReminderAttempt.from(pending));
        }

        return persistence.findForUpdate(
                        cohortMembershipId,
                        attendanceDate,
                        reminderType,
                        channel
                )
                .filter(reminder -> reminder.canRetry(startedAt.minus(PENDING_TIMEOUT)))
                .map(reminder -> {
                    reminder.retry(startedAt);
                    return AttendanceReminderAttempt.from(reminder);
                });
    }

    @Transactional
    public void markSent(AttendanceReminderAttempt attempt) {
        findAttemptForUpdate(attempt).ifPresent(reminder ->
                reminder.markSent(attempt.attemptCount(), OffsetDateTime.now(clock))
        );
    }

    @Transactional
    public void markSkipped(AttendanceReminderAttempt attempt, String reason) {
        findAttemptForUpdate(attempt).ifPresent(reminder ->
                reminder.markSkipped(
                        attempt.attemptCount(),
                        reason,
                        OffsetDateTime.now(clock)
                )
        );
    }

    @Transactional
    public void markFailed(AttendanceReminderAttempt attempt, String error) {
        findAttemptForUpdate(attempt).ifPresent(reminder ->
                reminder.markFailed(
                        attempt.attemptCount(),
                        error,
                        OffsetDateTime.now(clock)
                )
        );
    }

    private Optional<AttendanceReminder> findAttemptForUpdate(
            AttendanceReminderAttempt attempt
    ) {
        return persistence.findForUpdate(
                attempt.cohortMembershipId(),
                attempt.attendanceDate(),
                attempt.reminderType(),
                attempt.channel()
        );
    }
}
