package site.omagotchi.learningservice.global.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;

import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.global.exception.CommonErrorCode;

public final class StudyTimeParser {

    // ResolverStyle.STRICT로 실제로 존재하는 날짜인지 엄격하게 검사 (예: 2월 30일, 25시 60분 등)
    private static final DateTimeFormatter COMPACT_DATE_FORMATTER = DateTimeFormatter
            .ofPattern("uuuuMMdd", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter COMPACT_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("HHmm", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);

    private StudyTimeParser() {
    }

    /**
     * 날짜 문자열(yyyyMMdd 또는 yyyy-MM-dd)과 시간 문자열(HHmm 또는 HH:mm)을 받아
     * 시스템 정책 타임존(DateTimePolicy.ZONE_ID) 기준의 Instant로 변환합니다.
     */
    public static Instant parseToInstant(String dateStr, String timeStr) {
        if (dateStr == null || timeStr == null) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }

        LocalDate date = parseDate(dateStr.trim());
        LocalTime time = parseTime(timeStr.trim());

        ZonedDateTime zonedDateTime = ZonedDateTime.of(date, time, DateTimePolicy.ZONE_ID);
        return zonedDateTime.toInstant();
    }

    /**
     * 주어진 Instant가 분 단위(00초, 00나노초)로 딱 떨어지는지 검증합니다.
     */
    public static boolean isMinuteAligned(Instant instant) {
        if (instant == null) {
            return false;
        }
        return instant.getEpochSecond() % 60 == 0 && instant.getNano() == 0;
    }

    private static LocalDate parseDate(String dateStr) {
        try {
            if (dateStr.contains("-")) {
                return LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
            }
            return LocalDate.parse(dateStr, COMPACT_DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
    }

    private static LocalTime parseTime(String timeStr) {
        try {
            if (timeStr.contains(":")) {
                return LocalTime.parse(timeStr, DateTimeFormatter.ISO_LOCAL_TIME);
            }
            return LocalTime.parse(timeStr, COMPACT_TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
    }
}
