package site.omagotchi.learningservice.attendance.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import site.omagotchi.learningservice.attendance.application.result.AttendanceCleanupTarget;
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

    /**
     * 소속 종료 시 마감해야 할 미퇴실 출결을 ID 순서로 조회한다.
     *
     * <p>{@code checkedOutAt IS NULL}만 사용하면 이미 {@code MISSING_CHECK_OUT}으로
     * 확정한 행도 영원히 다시 조회된다. 자동 상태가 아직 확정되지 않았거나 열린 체류
     * 구간이 실제로 남아 있는 경우만 대상으로 삼는다.</p>
     */
    @Query("""
            select new site.omagotchi.learningservice.attendance.application.result.AttendanceCleanupTarget(
                       record.id, record.cohortMembershipId)
              from AttendanceRecord record
             where record.cohortMembershipId = :cohortMembershipId
               and record.checkedInAt is not null
               and record.checkedOutAt is null
               and (
                    record.autoStatus <> site.omagotchi.learningservice.attendance.domain.AttendanceStatus.MISSING_CHECK_OUT
                    or exists (
                        select presence.id
                          from PresenceInterval presence
                         where presence.attendanceId = record.id
                           and presence.endedAt is null
                    )
               )
             order by record.id asc
            """)
    List<AttendanceCleanupTarget> findEndCleanupTargetsByCohortMembershipId(
            @Param("cohortMembershipId") Long cohortMembershipId
    );
}
