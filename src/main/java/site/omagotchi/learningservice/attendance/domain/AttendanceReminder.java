package site.omagotchi.learningservice.attendance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;

/** 한 사람에게 한 날짜의 출결 알림을 발송한 시도와 결과 이력. */
@Entity
@Table(name = "attendance_reminders", schema = "learning_service")
public class AttendanceReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cohort_membership_id", nullable = false, updatable = false)
    private Long cohortMembershipId;

    @Column(name = "attendance_date", nullable = false, updatable = false)
    private LocalDate attendanceDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "reminder_type", nullable = false, updatable = false, length = 40)
    private ReminderType reminderType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 20)
    private ReminderChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReminderStatus status;

    @Column(name = "sent_at")
    private OffsetDateTime sentAt;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "telegram_message_id")
    private Long telegramMessageId;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected AttendanceReminder() {
    }

    /** 실제 발송을 시작하기 전에 진행 중 이력을 만든다. */
    public static AttendanceReminder pending(
            Long cohortMembershipId,
            LocalDate attendanceDate,
            ReminderType reminderType,
            ReminderChannel channel,
            OffsetDateTime startedAt
    ) {
        AttendanceReminder reminder = new AttendanceReminder();
        reminder.cohortMembershipId = Objects.requireNonNull(
                cohortMembershipId,
                "cohortMembershipId는 필수입니다."
        );
        reminder.attendanceDate = Objects.requireNonNull(
                attendanceDate,
                "attendanceDate는 필수입니다."
        );
        reminder.reminderType = Objects.requireNonNull(
                reminderType,
                "reminderType은 필수입니다."
        );
        reminder.channel = Objects.requireNonNull(channel, "channel은 필수입니다.");
        reminder.status = ReminderStatus.PENDING;
        reminder.attemptCount = 1;
        reminder.createdAt = Objects.requireNonNull(startedAt, "startedAt은 필수입니다.");
        reminder.updatedAt = startedAt;
        return reminder;
    }

    /** 실패·건너뜀 또는 제한 시간을 넘긴 진행 상태면 다시 시도할 수 있다. */
    public boolean canRetry(OffsetDateTime retryPendingBefore) {
        Objects.requireNonNull(retryPendingBefore, "retryPendingBefore는 필수입니다.");
        return switch (status) {
            case FAILED, SKIPPED -> true;
            case PENDING -> !updatedAt.isAfter(retryPendingBefore);
            case SENT -> false;
        };
    }

    /** 새 발송 시도를 시작하고 이전 실패 정보를 비운다. */
    public void retry(OffsetDateTime startedAt) {
        Objects.requireNonNull(startedAt, "startedAt은 필수입니다.");
        if (status == ReminderStatus.SENT) {
            throw new IllegalStateException("발송 완료된 알림은 다시 시도할 수 없습니다.");
        }
        status = ReminderStatus.PENDING;
        sentAt = null;
        lastError = null;
        attemptCount = Math.addExact(attemptCount, 1);
        updatedAt = startedAt;
    }

    /** 현재 발송 시도가 성공했을 때만 완료 상태로 전이한다. */
    public boolean markSent(int expectedAttemptCount, OffsetDateTime completedAt) {
        if (!isCurrentAttempt(expectedAttemptCount)) {
            return false;
        }
        status = ReminderStatus.SENT;
        sentAt = Objects.requireNonNull(completedAt, "completedAt은 필수입니다.");
        lastError = null;
        updatedAt = completedAt;
        return true;
    }

    /** 현재 발송 시도가 수신 설정 때문에 건너뛰어졌음을 기록한다. */
    public boolean markSkipped(
            int expectedAttemptCount,
            String reason,
            OffsetDateTime completedAt
    ) {
        if (!isCurrentAttempt(expectedAttemptCount)) {
            return false;
        }
        status = ReminderStatus.SKIPPED;
        sentAt = null;
        lastError = reason;
        updatedAt = Objects.requireNonNull(completedAt, "completedAt은 필수입니다.");
        return true;
    }

    /** 현재 발송 시도가 오류로 끝났음을 기록한다. */
    public boolean markFailed(
            int expectedAttemptCount,
            String error,
            OffsetDateTime completedAt
    ) {
        if (!isCurrentAttempt(expectedAttemptCount)) {
            return false;
        }
        status = ReminderStatus.FAILED;
        sentAt = null;
        lastError = error;
        updatedAt = Objects.requireNonNull(completedAt, "completedAt은 필수입니다.");
        return true;
    }

    private boolean isCurrentAttempt(int expectedAttemptCount) {
        return status == ReminderStatus.PENDING && attemptCount == expectedAttemptCount;
    }

    public Long getId() {
        return id;
    }

    public Long getCohortMembershipId() {
        return cohortMembershipId;
    }

    public LocalDate getAttendanceDate() {
        return attendanceDate;
    }

    public ReminderType getReminderType() {
        return reminderType;
    }

    public ReminderChannel getChannel() {
        return channel;
    }

    public ReminderStatus getStatus() {
        return status;
    }

    public OffsetDateTime getSentAt() {
        return sentAt;
    }

    public OffsetDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public Long getTelegramMessageId() {
        return telegramMessageId;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public String getLastError() {
        return lastError;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
