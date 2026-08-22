package site.omagotchi.learningservice.attendance.application.query;

import site.omagotchi.learningservice.attendance.domain.AttendanceErrorCode;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record AttendancePageQuery(
        LocalDate from,
        LocalDate to,
        int page,
        int size
) {
    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    public AttendancePageQuery {
        if (page < 0
                || size < 1
                || size > MAX_SIZE
                || from != null && to != null && (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) > 365)){
            throw new BusinessException(AttendanceErrorCode.ATTENDANCE_INVALID_PAGE_REQUEST);
        }
    }

    public static AttendancePageQuery of(LocalDate from, LocalDate to, Integer page, Integer size) {
        return new AttendancePageQuery(
                from,
                to,
                page == null ? DEFAULT_PAGE : page,
                size == null ? DEFAULT_SIZE : size
        );
    }
}
