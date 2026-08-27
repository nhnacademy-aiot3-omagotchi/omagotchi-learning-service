package site.omagotchi.learningservice.study.domain;

import site.omagotchi.learningservice.global.time.AggregationDateTime;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

public final class StudyTimePolicy {

    private StudyTimePolicy() {
    }

    public static boolean isMinuteAligned(Instant instant) {
        return instant.equals(floorToMinute(instant));
    }

    public static boolean isSecondAligned(Instant instant) {
        return instant.equals(instant.truncatedTo(ChronoUnit.SECONDS));
    }

    public static boolean isMinuteAligned(LocalTime time) {
        return time.equals(time.truncatedTo(ChronoUnit.MINUTES));
    }

    public static Instant floorToMinute(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MINUTES);
    }

    public static boolean crossesAggregationBoundary(Instant startTime, Instant endTime) {
        return !AggregationDateTime.aggregationDate(startTime)
                .equals(AggregationDateTime.aggregationDate(endTime.minusNanos(1)));
    }

    public static Optional<Instant> findCrossedAggregationBoundary(
            Instant startTime,
            Instant endTime
    ) {
        if (!crossesAggregationBoundary(startTime, endTime)) {
            return Optional.empty();
        }

        LocalDate nextAggregationDate = AggregationDateTime
                .aggregationDate(startTime)
                .plusDays(1);
        return Optional.of(AggregationDateTime.startOfAggregationDate(nextAggregationDate));
    }
}
