package site.omagotchi.learningservice.attendance.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import site.omagotchi.learningservice.attendance.domain.AttendanceRecord;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    Optional<AttendanceRecord> findByCohortMembershipIdAndAttendanceDate(
            Long cohortMembershipId,
            LocalDate attendanceDate
    );

    boolean existsByCohortMembershipIdAndAttendanceDate(
            Long cohortMembershipId,
            LocalDate attendanceDate
    );

    List<AttendanceRecord> findByCohortMembershipIdOrderByAttendanceDateDesc(Long cohortMembershipId);

    List<AttendanceRecord> findByAttendanceDateAndCohortMembershipIdInOrderByCohortMembershipIdAsc(
            LocalDate attendanceDate,
            List<Long> cohortMembershipIds
    );
}
