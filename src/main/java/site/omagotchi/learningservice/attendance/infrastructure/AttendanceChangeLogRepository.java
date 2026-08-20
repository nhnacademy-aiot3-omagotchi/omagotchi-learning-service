package site.omagotchi.learningservice.attendance.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.learningservice.attendance.domain.AttendanceChangeLog;

public interface AttendanceChangeLogRepository extends JpaRepository<AttendanceChangeLog, Long> {
}
