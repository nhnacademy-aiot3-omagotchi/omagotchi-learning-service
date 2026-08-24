package site.omagotchi.learningservice.attendance.application.query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import site.omagotchi.learningservice.attendance.application.AttendanceErrorCode;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("출결 페이지 조회 조건")
class AttendancePageQueryTest {

    @Test
    @DisplayName("페이지 기본값을 적용한다")
    void appliesDefaults() {
        AttendancePageQuery query = AttendancePageQuery.of(null, null, null, null);

        assertEquals(0, query.page());
        assertEquals(20, query.size());
    }

    @Test
    @DisplayName("역전되거나 366일을 넘는 날짜 범위를 거절한다")
    void rejectsInvalidDateRange() {
        LocalDate from = LocalDate.of(2026, 1, 1);

        BusinessException reversed = assertThrows(
                BusinessException.class,
                () -> AttendancePageQuery.of(from.plusDays(1), from, 0, 20)
        );
        BusinessException tooLong = assertThrows(
                BusinessException.class,
                () -> AttendancePageQuery.of(from, from.plusDays(366), 0, 20)
        );

        assertSame(AttendanceErrorCode.ATTENDANCE_INVALID_PAGE_REQUEST, reversed.getErrorCode());
        assertSame(AttendanceErrorCode.ATTENDANCE_INVALID_PAGE_REQUEST, tooLong.getErrorCode());
    }
}
