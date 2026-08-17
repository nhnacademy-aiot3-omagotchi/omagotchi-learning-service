package site.omagotchi.learningservice.statistics.application.query;

import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;
import site.omagotchi.learningservice.global.time.AggregationDateTime;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record WindowQuery(int days) {

    private static final int MIN_DAYS = 7;
    private static final int MAX_DAYS = 60;
    private static final Pattern WINDOW_PATTERN = Pattern.compile("^([1-9]\\d?)d$");

    public WindowQuery {
        if (days < MIN_DAYS || days > MAX_DAYS) {
            throw invalidRequest();
        }
    }

    public static WindowQuery parse(String value) {
        if (value == null) {
            throw invalidRequest();
        }

        Matcher matcher = WINDOW_PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw invalidRequest();
        }

        return new WindowQuery(Integer.parseInt(matcher.group(1)));
    }

    public DateRange resolveAt(Instant currentInstant) {
        if (currentInstant == null) {
            throw new IllegalArgumentException("currentInstant가 null입니다.");
        }

        LocalDate currentAggregationDate = AggregationDateTime.aggregationDate(currentInstant);
        return new DateRange(
                this,
                currentAggregationDate.minusDays(days - 1L),
                currentAggregationDate
        );
    }

    public String value() {
        return days + "d";
    }

    private static BusinessException invalidRequest() {
        return new BusinessException(CommonErrorCode.INVALID_REQUEST);
    }

    public record DateRange(
            WindowQuery window,
            LocalDate from,
            LocalDate to
    ) {

        public DateRange {
            if (window == null || from == null || to == null) {
                throw new IllegalArgumentException("조회 기간의 필수값이 누락되었습니다.");
            }
            long inclusiveDays = ChronoUnit.DAYS.between(from, to) + 1L;
            if (from.isAfter(to) || inclusiveDays != window.days()) {
                throw new IllegalArgumentException("조회 기간과 window가 일치하지 않습니다.");
            }
        }
    }
}
