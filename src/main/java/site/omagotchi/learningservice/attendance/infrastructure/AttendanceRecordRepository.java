package site.omagotchi.learningservice.attendance.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
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
            where record.id = :attendanceId
            """)
    Optional<AttendanceRecord> findByIdForUpdate(
            @Param("attendanceId") Long attendanceId
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

    Page<AttendanceRecord> findByCohortMembershipId(Long cohortMembershipId, Pageable pageable);

    Page<AttendanceRecord> findByCohortMembershipIdAndAttendanceDateBetween(
            Long cohortMembershipId,
            LocalDate from,
            LocalDate to,
            Pageable pageable
    );

    Page<AttendanceRecord> findByCohortMembershipIdAndAttendanceDateGreaterThanEqual(
            Long cohortMembershipId,
            LocalDate from,
            Pageable pageable
    );

    Page<AttendanceRecord> findByCohortMembershipIdAndAttendanceDateLessThanEqual(
            Long cohortMembershipId,
            LocalDate to,
            Pageable pageable
    );

    Page<AttendanceRecord> findByAttendanceDateAndCohortMembershipIdIn(
            LocalDate attendanceDate,
            List<Long> cohortMembershipIds,
            Pageable pageable
    );

    @Query("""
            select distinct record.attendanceDate
            from AttendanceRecord record
            where record.cohortMembershipId = :cohortMembershipId
              and record.attendanceDate <= :baseDate
              and record.checkedInAt is not null
            order by record.attendanceDate desc
            """)
    List<LocalDate> findDistinctAttendedDatesOnOrBefore(
            Long cohortMembershipId,
            LocalDate baseDate
    );
}
