package site.omagotchi.learningservice.attendance.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.attendance.application.port.AttendanceRecordQueryRepository;
import site.omagotchi.learningservice.attendance.domain.AttendanceRecord;

import java.time.LocalDate;
import java.util.List;

/**
 * 출결 기록 조회의 JPA 구현.
 *
 * <p>정렬 기준과 Pageable 생성, Page 변환을 이 계층 안에서만 처리한다.
 * Entity 필드 이름 문자열이 application으로 새어 나가지 않도록 상수로 고정한다.
 */
@Repository
@RequiredArgsConstructor
public class AttendanceRecordJpaPersistence implements AttendanceRecordQueryRepository {

    // 개인 이력은 최신 날짜 우선. 같은 날짜는 나중에 생성된 기록이 앞에 온다.
    private static final Sort MEMBER_RECORDS_SORT = Sort.by(
            Sort.Order.desc("attendanceDate"),
            Sort.Order.desc("id")
    );

    // 관리자 일자별 조회는 소속 순으로 고정해 페이지 간 순서가 흔들리지 않게 한다.
    private static final Sort DAILY_RECORDS_SORT = Sort.by(
            Sort.Order.asc("cohortMembershipId"),
            Sort.Order.asc("id")
    );

    private final AttendanceRecordRepository attendanceRecordRepository;

    @Override
    public AttendanceRecordPage findMemberRecords(
            Long cohortMembershipId,
            LocalDate from,
            LocalDate to,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(page, size, MEMBER_RECORDS_SORT);

        Page<AttendanceRecord> records;
        if (from != null && to != null) {
            records = attendanceRecordRepository.findByCohortMembershipIdAndAttendanceDateBetween(
                    cohortMembershipId, from, to, pageable
            );
        } else if (from != null) {
            records = attendanceRecordRepository
                    .findByCohortMembershipIdAndAttendanceDateGreaterThanEqual(
                            cohortMembershipId, from, pageable
                    );
        } else if (to != null) {
            records = attendanceRecordRepository
                    .findByCohortMembershipIdAndAttendanceDateLessThanEqual(
                            cohortMembershipId, to, pageable
                    );
        } else {
            records = attendanceRecordRepository.findByCohortMembershipId(
                    cohortMembershipId, pageable
            );
        }
        return toPage(records);
    }

    @Override
    public AttendanceRecordPage findDailyRecords(
            LocalDate attendanceDate,
            List<Long> cohortMembershipIds,
            int page,
            int size
    ) {
        // 빈 IN 조건은 DB마다 동작이 갈리므로 질의 전에 차단한다.
        if (cohortMembershipIds == null || cohortMembershipIds.isEmpty()) {
            return AttendanceRecordPage.empty(page, size);
        }

        Pageable pageable = PageRequest.of(page, size, DAILY_RECORDS_SORT);
        return toPage(attendanceRecordRepository.findByAttendanceDateAndCohortMembershipIdIn(
                attendanceDate, cohortMembershipIds, pageable
        ));
    }

    private static AttendanceRecordPage toPage(Page<AttendanceRecord> records) {
        return new AttendanceRecordPage(
                records.getContent(),
                records.getNumber(),
                records.getSize(),
                records.getTotalElements(),
                records.getTotalPages()
        );
    }
}
