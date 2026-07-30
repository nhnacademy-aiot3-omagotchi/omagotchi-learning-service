package site.omagotchi.learningservice.telegram.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Getter
@Entity
@Table(name = "attendance_reminders", schema = "learning_service")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AttendanceReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cohort_membership_id", nullable = false)
    private Long cohortMembershipId;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "reminder_type", nullable = false, length = 40)
    private ReminderType reminderType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
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

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static AttendanceReminder pending(
            Long cohortMembershipId,
            LocalDate attendanceDate,
            ReminderType reminderType,
            ReminderChannel channel
    ) {
        OffsetDateTime now = OffsetDateTime.now();

        AttendanceReminder reminder = new AttendanceReminder();
        reminder.cohortMembershipId = cohortMembershipId;
        reminder.attendanceDate = requireDate(attendanceDate);
        reminder.reminderType = reminderType;
        reminder.channel = channel;
        reminder.status = ReminderStatus.PENDING;
        reminder.attemptCount = 0;
        reminder.createdAt = now;
        reminder.updatedAt = now;
        return reminder;
    }

    public void markSent(Long telegramMessageId) {
        OffsetDateTime now = OffsetDateTime.now();

        this.status = ReminderStatus.SENT;
        this.sentAt = now;
        this.telegramMessageId = telegramMessageId;
        this.lastError = null;
        this.updatedAt = now;
    }

    public void markFailed(String lastError) {
        this.status = ReminderStatus.FAILED;
        this.attemptCount += 1;
        this.lastError = truncate(lastError, 500);
        this.updatedAt = OffsetDateTime.now();
    }

    public void markSkipped(String reason) {
        this.status = ReminderStatus.SKIPPED;
        this.lastError = truncate(reason, 500);
        this.updatedAt = OffsetDateTime.now();
    }

    public void confirm() {
        this.confirmedAt = OffsetDateTime.now();
        this.updatedAt = this.confirmedAt;
    }

    private static LocalDate requireDate(LocalDate value) {
        if (value == null) {
            throw new IllegalArgumentException("attendanceDate는 필수입니다.");
        }
        return value;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
