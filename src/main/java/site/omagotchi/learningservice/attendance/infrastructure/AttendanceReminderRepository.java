package site.omagotchi.learningservice.attendance.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.omagotchi.learningservice.attendance.domain.AttendanceReminder;
import site.omagotchi.learningservice.attendance.domain.ReminderChannel;
import site.omagotchi.learningservice.attendance.domain.ReminderType;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface AttendanceReminderRepository extends JpaRepository<AttendanceReminder, Long> {

    @Query("""
            select reminder.cohortMembershipId
              from AttendanceReminder reminder
             where reminder.attendanceDate = :attendanceDate
               and reminder.reminderType = :reminderType
               and reminder.channel = :channel
               and reminder.cohortMembershipId in :membershipIds
            """)
    List<Long> findRecordedMembershipIds(
            @Param("attendanceDate") LocalDate attendanceDate,
            @Param("reminderType") ReminderType reminderType,
            @Param("channel") ReminderChannel channel,
            @Param("membershipIds") Collection<Long> membershipIds
    );
}
