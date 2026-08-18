package site.omagotchi.learningservice.sensor.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public enum SeriesWindow {


    DAY  (Duration.ofDays(1),  ChronoUnit.HOURS, SeriesBucket.AVG_1H, SeriesBucket.RAW),
    WEEK (Duration.ofDays(7),  ChronoUnit.HOURS, SeriesBucket.AVG_1H, SeriesBucket.RAW),
    MONTH(Duration.ofDays(30), ChronoUnit.DAYS,  SeriesBucket.AVG_1D, SeriesBucket.AVG_1H);

    private final Duration range;               // 조회 범위
    private final ChronoUnit interval;          // 간격
    private final SeriesBucket settledBucket;   // 확정 구간
    private final SeriesBucket hotBucket;       // 진행 중 구간

    SeriesWindow(Duration range, ChronoUnit interval, SeriesBucket settledBucket, SeriesBucket hotBucket) {
        this.range = range;
        this.interval = interval;
        this.settledBucket = settledBucket;
        this.hotBucket = hotBucket;
    }

    /** 확정 구간과 진행 중 구간의 경계.
     * 현재 시각을 간격 단위로 내림한 지점. */
    public Instant settledUntil(Instant now, ZoneId zone) {
        return now
                .atZone(zone)
                .truncatedTo(interval)
                .toInstant();
    }

    /** 조회 시작 시각. */
    public Instant from(Instant now) {
        return now.minus(range);
    }

    /** Flux에 넘길 간격 문자열. */
    public String fluxInterval() {
        return interval == ChronoUnit.HOURS ? "1h" : "1d";
    }

    public SeriesBucket settledBucket() {
        return settledBucket;
    }

    public SeriesBucket hotBucket() {
        return hotBucket;
    }
}