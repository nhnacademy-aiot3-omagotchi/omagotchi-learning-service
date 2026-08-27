package site.omagotchi.learningservice.telegram.application.result;

import site.omagotchi.learningservice.telegram.domain.AttendanceReminder;
import site.omagotchi.learningservice.telegram.domain.ReminderChannel;
import site.omagotchi.learningservice.telegram.domain.ReminderStatus;
import site.omagotchi.learningservice.telegram.domain.ReminderType;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record AttendanceReminderResponse(
        Long id,
        Long cohortMembershipId,
        LocalDate attendanceDate,
        ReminderType reminderType,
        ReminderChannel channel,
        ReminderStatus status,
        OffsetDateTime sentAt,
        OffsetDateTime confirmedAt,
        Long telegramMessageId,
        Integer attemptCount,
        String lastError,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static AttendanceReminderResponse from(AttendanceReminder reminder) {
        return new AttendanceReminderResponse(
                reminder.getId(),
                reminder.getCohortMembershipId(),
                reminder.getAttendanceDate(),
                reminder.getReminderType(),
                reminder.getChannel(),
                reminder.getStatus(),
                reminder.getSentAt(),
                reminder.getConfirmedAt(),
                reminder.getTelegramMessageId(),
                reminder.getAttemptCount(),
                reminder.getLastError(),
                reminder.getCreatedAt(),
                reminder.getUpdatedAt()
        );
    }
}
