package site.omagotchi.learningservice.attendance.application.port;

import site.omagotchi.learningservice.attendance.domain.AttendanceReminder;
import site.omagotchi.learningservice.attendance.domain.ReminderChannel;
import site.omagotchi.learningservice.attendance.domain.ReminderType;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 출결 알림 이력을 조회하고 저장하는 DB 경계. */
public interface AttendanceReminderPersistence {

    /** SENT와 아직 제한 시간을 넘기지 않은 PENDING 이력의 소속 ID를 조회한다. */
    List<Long> findBlockingMembershipIds(
            LocalDate attendanceDate,
            ReminderType reminderType,
            ReminderChannel channel,
            Collection<Long> membershipIds,
            OffsetDateTime retryPendingBefore
    );

    /** 같은 자연 키의 이력이 없을 때만 PENDING 이력을 삽입한다. */
    boolean insertIfAbsent(AttendanceReminder reminder);

    Optional<AttendanceReminder> findForUpdate(
            Long cohortMembershipId,
            LocalDate attendanceDate,
            ReminderType reminderType,
            ReminderChannel channel
    );
}
