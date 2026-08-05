package site.omagotchi.learningservice.attendance.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import site.omagotchi.learningservice.attendance.domain.AttendanceRecord;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    Optional<AttendanceRecord> findByCohortMembershipIdAndAttendanceDate(
            Long cohortMembershipId,
            LocalDate attendanceDate
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select record
            from AttendanceRecord record
            where record.cohortMembershipId = :cohortMembershipId
              and record.attendanceDate = :attendanceDate
            """)
    Optional<AttendanceRecord> findWithLockByCohortMembershipIdAndAttendanceDate(
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
