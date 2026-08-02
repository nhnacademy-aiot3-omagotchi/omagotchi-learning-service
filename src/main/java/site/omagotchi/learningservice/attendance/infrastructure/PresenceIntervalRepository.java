package site.omagotchi.learningservice.attendance.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.learningservice.attendance.domain.PresenceInterval;

import java.util.Optional;

public interface PresenceIntervalRepository extends JpaRepository<PresenceInterval, Long> {

    Optional<PresenceInterval> findFirstByAttendanceIdAndEndedAtIsNullOrderByStartedAtDesc(Long attendanceId);
}
