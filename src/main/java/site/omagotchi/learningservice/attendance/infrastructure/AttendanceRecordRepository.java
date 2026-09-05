package site.omagotchi.learningservice.attendance.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.omagotchi.learningservice.attendance.application.result.AttendanceCleanupTarget;
import site.omagotchi.learningservice.attendance.domain.AttendanceRecord;

import java.time.LocalDate;
import java.util.Collection;
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

    /**
     * 종료 소속 출결 정합성 스윕의 커서 조회.
     *
     * <p>{@code cohort_memberships}를 조인하지 않는다. 이 저장소는 출결 쪽의 미해결 후보만
     * 반환하고, 소속 활성 여부는 Application이 기수 기능의 공개 조회 계약에 묻는다.</p>
     *
     * <p>이미 {@code MISSING_CHECK_OUT}으로 확정됐더라도 열린 체류가 남았으면 다시
     * 반환한다. 상태 확정과 체류 마감 사이에 부분 실패가 있었던 행도 복구해야 하기
     * 때문이다.</p>
     */
    @Query("""
            select new site.omagotchi.learningservice.attendance.application.result.AttendanceCleanupTarget(
                       record.id, record.cohortMembershipId)
              from AttendanceRecord record
             where record.id > :afterAttendanceId
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
    List<AttendanceCleanupTarget> findEndCleanupTargetsPageAfter(
            @Param("afterAttendanceId") Long afterAttendanceId,
            Pageable pageable
    );

    /** JPQL에 없는 {@code LIMIT}을 크기만 지정한 {@link Pageable}로 적용한다. */
    default List<AttendanceCleanupTarget> findEndCleanupTargetsAfter(
            Long afterAttendanceId,
            int limit
    ) {
        return findEndCleanupTargetsPageAfter(afterAttendanceId, PageRequest.ofSize(limit));
    }

    /** 기수 종료 정리 대상이 있는 소속만 ID 순서로 좁힌다. */
    @Query("""
            select distinct record.cohortMembershipId
              from AttendanceRecord record
             where record.cohortMembershipId in :cohortMembershipIds
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
             order by record.cohortMembershipId asc
            """)
    List<Long> findDistinctEndCleanupMembershipIds(
            @Param("cohortMembershipIds") Collection<Long> cohortMembershipIds
    );
}
