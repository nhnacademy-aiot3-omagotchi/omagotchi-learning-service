package site.omagotchi.learningservice.attendance.application.result;

import site.omagotchi.learningservice.attendance.domain.AttendanceReminder;
import site.omagotchi.learningservice.attendance.domain.ReminderChannel;
import site.omagotchi.learningservice.attendance.domain.ReminderType;

import java.time.LocalDate;

/** 비동기 발송 완료가 자신이 시작한 시도만 확정하게 하는 식별 값. */
public record AttendanceReminderAttempt(
        Long cohortMembershipId,
        LocalDate attendanceDate,
        ReminderType reminderType,
        ReminderChannel channel,
        int attemptCount
) {

    public static AttendanceReminderAttempt from(AttendanceReminder reminder) {
        return new AttendanceReminderAttempt(
                reminder.getCohortMembershipId(),
                reminder.getAttendanceDate(),
                reminder.getReminderType(),
                reminder.getChannel(),
                reminder.getAttemptCount()
        );
    }
}
