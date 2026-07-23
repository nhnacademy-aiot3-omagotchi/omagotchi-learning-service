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

    /**
     * UTC 타임라인의 현재 시각을 반환
     */
    public Instant currentInstant() {
        return clock.instant();
    }

    /**
     * Instant를 서비스 지역 시간으로 변환
     */
    public ZonedDateTime toZonedDateTime(Instant instant) {
        return instant.atZone(DateTimePolicy.ZONE_ID);
    }

    /**
     * 집계 기준일의 시작 시각을 UTC 타임라인의 Instant로 반환
     */
    public Instant startOfAggregationDate(LocalDate aggregationDate) {
        return aggregationDate
                .atTime(DateTimePolicy.DAILY_RESET_TIME)
                .atZone(DateTimePolicy.ZONE_ID)
                .toInstant();
    }

    /**
     * 집계 기준일의 종료 배타 시각을 UTC 타임라인의 Instant로 반환
     */
    public Instant endExclusiveOfAggregationDate(LocalDate aggregationDate) {
        return startOfAggregationDate(aggregationDate.plusDays(1));
    }

    /**
     * Instant를 기반으로 집계 기준일(Aggregation Date)을 계산
     */
    public LocalDate calculateAggregationDate(Instant instant) {
        LocalDate localDate = toZonedDateTime(instant).toLocalDate();
        Instant resetBoundary = startOfAggregationDate(localDate);

        return instant.isBefore(resetBoundary)
                ? localDate.minusDays(1)
                : localDate;
    }
}
