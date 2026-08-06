package site.omagotchi.learningservice.global.util;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;

@Component
@RequiredArgsConstructor
public class DateTimeProvider {

    private final Clock clock;

    public Instant currentInstant() {
        return clock.instant();
    }

    public LocalDate currentAggregationDate() {
        return calculateAggregationDate(currentInstant());
    }

    public ZonedDateTime toZonedDateTime(Instant instant) {
        return instant.atZone(DateTimePolicy.ZONE_ID);
    }

    public Instant startOfAggregationDate(LocalDate aggregationDate) {
        return aggregationDate
                .atTime(DateTimePolicy.DAILY_RESET_TIME)
                .atZone(DateTimePolicy.ZONE_ID)
                .toInstant();
    }

    public Instant endExclusiveOfAggregationDate(LocalDate aggregationDate) {
        return startOfAggregationDate(aggregationDate.plusDays(1));
    }

    public LocalDate calculateAggregationDate(Instant instant) {
        LocalDate localDate = toZonedDateTime(instant).toLocalDate();
        Instant resetBoundary = startOfAggregationDate(localDate);

        return instant.isBefore(resetBoundary)
                ? localDate.minusDays(1)
                : localDate;
    }

    public boolean crossesAggregationBoundary(Instant startTime, Instant endTime) {
        LocalDate startAggregationDate = calculateAggregationDate(startTime);
        LocalDate endAggregationDate = calculateAggregationDate(endTime.minusNanos(1));
        return !startAggregationDate.equals(endAggregationDate);
    }
}
