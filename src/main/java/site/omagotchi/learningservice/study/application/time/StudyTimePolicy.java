package site.omagotchi.learningservice.study.application.time;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StudyTimePolicy {

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final LocalTime DAILY_RESET_TIME = LocalTime.of(4, 0);

    public static Instant toInstant(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(ZONE_ID).toInstant();
    }

    public static LocalDate aggregationDate(Instant instant) {
        LocalDate localDate = instant.atZone(ZONE_ID).toLocalDate();

        return instant.isBefore(startOfAggregationDate(localDate))
                ? localDate.minusDays(1)
                : localDate;
    }

    public static boolean crossesAggregationBoundary(Instant startTime, Instant endTime) {
        return !aggregationDate(startTime).equals(aggregationDate(endTime.minusNanos(1)));
    }

    private static Instant startOfAggregationDate(LocalDate aggregationDate) {
        return aggregationDate.atTime(DAILY_RESET_TIME).atZone(ZONE_ID).toInstant();
    }
}
