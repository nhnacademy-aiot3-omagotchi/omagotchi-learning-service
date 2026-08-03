package site.omagotchi.learningservice.study.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

public final class StudyTimePolicy {

    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Seoul");
    private static final LocalTime DAILY_RESET_TIME = LocalTime.of(4, 0);

    private StudyTimePolicy() {
    }

    public static Instant toInstant(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(ZONE_ID).toInstant();
    }

    public static boolean isMinuteAligned(Instant instant) {
        return instant.equals(floorToMinute(instant));
    }

    public static boolean isMinuteAligned(LocalTime time) {
        return time.equals(time.truncatedTo(ChronoUnit.MINUTES));
    }

    public static Instant floorToMinute(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MINUTES);
    }

    public static Instant ceilToMinute(Instant instant) {
        Instant floor = floorToMinute(instant);
        return instant.equals(floor) ? floor : floor.plus(1, ChronoUnit.MINUTES);
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

    public static Optional<Instant> findCrossedAggregationBoundary(
            Instant startTime,
            Instant endTime
    ) {
        if (!crossesAggregationBoundary(startTime, endTime)) {
            return Optional.empty();
        }

        LocalDate nextAggregationDate = aggregationDate(startTime).plusDays(1);
        return Optional.of(startOfAggregationDate(nextAggregationDate));
    }

    private static Instant startOfAggregationDate(LocalDate aggregationDate) {
        return aggregationDate.atTime(DAILY_RESET_TIME).atZone(ZONE_ID).toInstant();
    }
}
