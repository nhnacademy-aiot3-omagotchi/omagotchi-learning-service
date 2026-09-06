package site.omagotchi.learningservice.attendance.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.attendance.application.port.AttendanceReminderPersistence;
import site.omagotchi.learningservice.attendance.domain.AttendanceReminder;
import site.omagotchi.learningservice.attendance.domain.ReminderChannel;
import site.omagotchi.learningservice.attendance.domain.ReminderType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 출결 알림 이력 DB 경계의 JPA 구현. */
@Repository
@RequiredArgsConstructor
public class AttendanceReminderJpaPersistence implements AttendanceReminderPersistence {

    private final AttendanceReminderRepository repository;

    @Override
    public List<Long> findBlockingMembershipIds(
            LocalDate attendanceDate,
            ReminderType reminderType,
            ReminderChannel channel,
            Collection<Long> membershipIds,
            OffsetDateTime retryPendingBefore
    ) {
        if (membershipIds == null || membershipIds.isEmpty()) {
            return List.of();
        }
        return repository.findBlockingMembershipIds(
                attendanceDate,
                reminderType,
                channel,
                membershipIds,
                retryPendingBefore
        );
    }

    @Override
    public boolean insertIfAbsent(AttendanceReminder reminder) {
        return repository.insertPendingIfAbsent(
                reminder.getCohortMembershipId(),
                reminder.getAttendanceDate(),
                reminder.getReminderType().name(),
                reminder.getChannel().name(),
                reminder.getAttemptCount(),
                reminder.getCreatedAt(),
                reminder.getUpdatedAt()
        ) == 1;
    }

    @Override
    public Optional<AttendanceReminder> findForUpdate(
            Long cohortMembershipId,
            LocalDate attendanceDate,
            ReminderType reminderType,
            ReminderChannel channel
    ) {
        return repository.findForUpdate(
                cohortMembershipId,
                attendanceDate,
                reminderType,
                channel
        );
    }
}
