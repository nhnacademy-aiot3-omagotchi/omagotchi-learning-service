package site.omagotchi.learningservice.attendance.application.port;

import site.omagotchi.learningservice.attendance.domain.AttendanceReminder;
import site.omagotchi.learningservice.attendance.domain.ReminderChannel;
import site.omagotchi.learningservice.attendance.domain.ReminderType;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/** 출결 알림 이력을 조회하고 저장하는 DB 경계. */
public interface AttendanceReminderPersistence {

    List<Long> findRecordedMembershipIds(
            LocalDate attendanceDate,
            ReminderType reminderType,
            ReminderChannel channel,
            Collection<Long> membershipIds
    );

    /** 동시 실행이 먼저 같은 이력을 저장했으면 {@code false}를 반환한다. */
    boolean saveIfAbsent(AttendanceReminder reminder);
}
