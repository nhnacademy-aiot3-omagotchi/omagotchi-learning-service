package site.omagotchi.learningservice.sensor.domain;

import java.time.Instant;

/** 차트의 점 하나.
 * value가 null이면 그 시간대에 수집이 없었다는 뜻. */
public record SeriesPoint(
        Instant time,
        Double value,
        boolean partial
) {
}