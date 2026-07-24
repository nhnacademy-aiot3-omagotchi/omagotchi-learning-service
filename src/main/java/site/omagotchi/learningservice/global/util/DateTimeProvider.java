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

    /**
     * 반개구간 {@code [startTime, endTime)}이 KST 04:00 집계 경계를 넘는지 확인한다.
     * 종료 시각이 경계와 정확히 같으면 해당 경계를 점유하지 않으므로 교차하지 않는다.
     */
    public boolean crossesAggregationBoundary(
            Instant startTime,
            Instant endTime
    ) {
        LocalDate startAggregationDate = calculateAggregationDate(startTime);
        LocalDate endAggregationDate = calculateAggregationDate(endTime.minusNanos(1));
        // 시작과 종료의 기준일이 다른지 체크
        return !startAggregationDate.equals(endAggregationDate);
    }
}
