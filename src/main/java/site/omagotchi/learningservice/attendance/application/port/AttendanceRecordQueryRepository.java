package site.omagotchi.learningservice.attendance.application.port;

import site.omagotchi.learningservice.attendance.domain.AttendanceRecord;

import java.time.LocalDate;
import java.util.List;

/**
 * 출결 기록 목록 조회 경계.
 *
 * <p>페이지 번호와 크기는 업무 조건이지만, 정렬 기준과 페이징 구현은 저장소 기술의 관심사다.
 * 계약에 Pageable, Page, Sort 같은 기술 Type을 노출하지 않고 조회 조건과 결과만 표현한다.
 * 정렬 기준은 구현체가 소유하며, 조회 목적이 달라지면 메서드를 나눈다.
 */
public interface AttendanceRecordQueryRepository {

    /**
     * 특정 소속의 출결 기록을 최신순으로 조회한다.
     *
     * @param from 조회 시작일. {@code null}이면 하한 없음
     * @param to   조회 종료일. {@code null}이면 상한 없음
     */
    AttendanceRecordPage findMemberRecords(
            Long cohortMembershipId,
            LocalDate from,
            LocalDate to,
            int page,
            int size
    );

    /**
     * 특정 일자의 여러 소속 출결 기록을 소속 순으로 조회한다.
     */
    AttendanceRecordPage findDailyRecords(
            LocalDate attendanceDate,
            List<Long> cohortMembershipIds,
            int page,
            int size
    );

    /**
     * 페이지 조회 결과의 업무용 표현.
     *
     * <p>저장소 기술의 Page Type을 application으로 내보내지 않기 위한 계약이다.
     */
    record AttendanceRecordPage(
            List<AttendanceRecord> items,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        public AttendanceRecordPage {
            items = items == null ? List.of() : List.copyOf(items);
        }

        public static AttendanceRecordPage empty(int page, int size) {
            return new AttendanceRecordPage(List.of(), page, size, 0L, 0);
        }
    }
}
