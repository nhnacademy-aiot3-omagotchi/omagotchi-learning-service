package site.omagotchi.learningservice.global.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    public static Instant toInstant(LocalDateTime dateTime) {
        return dateTime.atZone(ZONE_ID).toInstant();
    }

    /**
     * 지금 시점의 집계일.
     *
     * <p>{@code LocalDate.now()}는 JVM 기본 타임존을 따르므로 서버·CI가 UTC면 KST와 날짜가
     * 하루 어긋난다. 출결·학습 기록은 모두 이 집계일을 키로 저장하므로, "오늘"이 필요한
     * 곳은 반드시 이 메서드를 쓴다.</p>
     */
    public static LocalDate today(Clock clock) {
        return aggregationDate(clock.instant());
    }

    /** {@link Clock} 빈을 주입받지 않는 자리(주로 테스트)에서 쓰는 오버로드다. */
    public static LocalDate today() {
        return aggregationDate(Instant.now());
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
