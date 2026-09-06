package site.omagotchi.learningservice.attendance.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.attendance.application.port.AttendanceReminderPersistence;
import site.omagotchi.learningservice.attendance.domain.AttendanceReminder;
import site.omagotchi.learningservice.attendance.domain.ReminderChannel;
import site.omagotchi.learningservice.attendance.domain.ReminderType;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/** 출결 알림 이력 DB 경계의 JPA 구현. */
@Repository
@RequiredArgsConstructor
public class AttendanceReminderJpaPersistence implements AttendanceReminderPersistence {

    private final AttendanceReminderRepository repository;

    @Override
    public List<Long> findRecordedMembershipIds(
            LocalDate attendanceDate,
            ReminderType reminderType,
            ReminderChannel channel,
            Collection<Long> membershipIds
    ) {
        if (membershipIds == null || membershipIds.isEmpty()) {
            return List.of();
        }
        return repository.findRecordedMembershipIds(
                attendanceDate,
                reminderType,
                channel,
                membershipIds
        );
    }

    @Override
    public boolean saveIfAbsent(AttendanceReminder reminder) {
        try {
            // flush까지 끝내 유니크 제약을 발송보다 먼저 판정한다.
            repository.saveAndFlush(reminder);
            return true;
        } catch (DataIntegrityViolationException exception) {
            return false;
        }
    }
}
