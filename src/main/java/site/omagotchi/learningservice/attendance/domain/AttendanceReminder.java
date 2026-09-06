package site.omagotchi.learningservice.attendance.domain;

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
import java.time.ZoneOffset;
import java.util.Objects;

/** 한 사람에게 한 날짜의 출결 알림을 보냈다는 이력. */
@Getter
@Entity
@Table(name = "attendance_reminders", schema = "learning_service")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    /**
     * 발송 직전에 이력을 만든다. 이 행의 존재 자체가 발송 여부이므로 별도 중간 상태를
     * 거치지 않는다.
     */
    public static AttendanceReminder sent(
            Long cohortMembershipId,
            LocalDate attendanceDate,
            ReminderType reminderType,
            ReminderChannel channel
    ) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

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
        reminder.status = ReminderStatus.SENT;
        reminder.sentAt = now;
        reminder.attemptCount = 0;
        reminder.createdAt = now;
        reminder.updatedAt = now;
        return reminder;
    }
}
