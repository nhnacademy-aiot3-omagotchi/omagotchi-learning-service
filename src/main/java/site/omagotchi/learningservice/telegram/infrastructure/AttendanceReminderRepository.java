package site.omagotchi.learningservice.telegram.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.learningservice.telegram.domain.AttendanceReminder;
import site.omagotchi.learningservice.telegram.domain.ReminderChannel;
import site.omagotchi.learningservice.telegram.domain.ReminderType;

import java.time.LocalDate;
import java.util.Optional;

public interface AttendanceReminderRepository extends JpaRepository<AttendanceReminder, Long> {

    Optional<AttendanceReminder> findByCohortMembershipIdAndAttendanceDateAndReminderTypeAndChannel(
            Long cohortMembershipId,
            LocalDate attendanceDate,
            ReminderType reminderType,
            ReminderChannel channel
    );
}
