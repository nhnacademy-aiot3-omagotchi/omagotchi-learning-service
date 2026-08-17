package site.omagotchi.learningservice.global.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * 서비스 전체에서 사용하는 KST 04:00 집계일 기준.
 */
public final class AggregationDateTime {

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final LocalTime DAILY_RESET_TIME = LocalTime.of(4, 0);

    private AggregationDateTime() {
    }

    public static Instant toInstant(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(ZONE_ID).toInstant();
    }

    public static LocalDate aggregationDate(Instant instant) {
        LocalDate localDate = instant.atZone(ZONE_ID).toLocalDate();

        return instant.isBefore(startOfAggregationDate(localDate))
                ? localDate.minusDays(1)
                : localDate;
    }

    public static Instant startOfAggregationDate(LocalDate aggregationDate) {
        return aggregationDate.atTime(DAILY_RESET_TIME).atZone(ZONE_ID).toInstant();
    }
}
