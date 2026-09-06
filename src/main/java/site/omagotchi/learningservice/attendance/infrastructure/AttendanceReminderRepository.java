package site.omagotchi.learningservice.attendance.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.omagotchi.learningservice.attendance.domain.AttendanceReminder;
import site.omagotchi.learningservice.attendance.domain.ReminderChannel;
import site.omagotchi.learningservice.attendance.domain.ReminderType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AttendanceReminderRepository extends JpaRepository<AttendanceReminder, Long> {

    @Query("""
            select reminder.cohortMembershipId
              from AttendanceReminder reminder
             where reminder.attendanceDate = :attendanceDate
               and reminder.reminderType = :reminderType
               and reminder.channel = :channel
               and reminder.cohortMembershipId in :membershipIds
               and (
                    reminder.status = site.omagotchi.learningservice.attendance.domain.ReminderStatus.SENT
                    or (
                        reminder.status = site.omagotchi.learningservice.attendance.domain.ReminderStatus.PENDING
                        and reminder.updatedAt > :retryPendingBefore
                    )
               )
            """)
    List<Long> findBlockingMembershipIds(
            @Param("attendanceDate") LocalDate attendanceDate,
            @Param("reminderType") ReminderType reminderType,
            @Param("channel") ReminderChannel channel,
            @Param("membershipIds") Collection<Long> membershipIds,
            @Param("retryPendingBefore") OffsetDateTime retryPendingBefore
    );

    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into learning_service.attendance_reminders (
                cohort_membership_id,
                attendance_date,
                reminder_type,
                channel,
                status,
                attempt_count,
                created_at,
                updated_at
            ) values (
                :cohortMembershipId,
                :attendanceDate,
                :reminderType,
                :channel,
                'PENDING',
                :attemptCount,
                :createdAt,
                :updatedAt
            )
            on conflict (cohort_membership_id, attendance_date, reminder_type, channel)
            do nothing
            """, nativeQuery = true)
    int insertPendingIfAbsent(
            @Param("cohortMembershipId") Long cohortMembershipId,
            @Param("attendanceDate") LocalDate attendanceDate,
            @Param("reminderType") String reminderType,
            @Param("channel") String channel,
            @Param("attemptCount") Integer attemptCount,
            @Param("createdAt") OffsetDateTime createdAt,
            @Param("updatedAt") OffsetDateTime updatedAt
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select reminder
              from AttendanceReminder reminder
             where reminder.cohortMembershipId = :cohortMembershipId
               and reminder.attendanceDate = :attendanceDate
               and reminder.reminderType = :reminderType
               and reminder.channel = :channel
            """)
    Optional<AttendanceReminder> findForUpdate(
            @Param("cohortMembershipId") Long cohortMembershipId,
            @Param("attendanceDate") LocalDate attendanceDate,
            @Param("reminderType") ReminderType reminderType,
            @Param("channel") ReminderChannel channel
    );
}
