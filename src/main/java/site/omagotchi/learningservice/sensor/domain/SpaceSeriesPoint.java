package site.omagotchi.learningservice.sensor.domain;

import java.time.Instant;

/** 한 시간대의 공간 집계. 수집이 없었으면 count가 0이고 나머지는 null이다. */
public record SpaceSeriesPoint(
        Instant time,
        Double avg,
        Double min,
        String minDeviceEui,
        Double max,
        String maxDeviceEui,
        int count,
        boolean partial
) {

    public SpaceSeriesPoint {
        if (count < 0) {
            throw new IllegalArgumentException("count는 0 이상이어야 합니다: " + count);
        }
        if (count == 0 && (avg != null || min != null || max != null)) {
            throw new IllegalArgumentException("수집이 없으면 집계값이 없어야 합니다: " + time);
        }
        if (count > 0 && (avg == null || min == null || max == null)) {
            throw new IllegalArgumentException("수집이 있으면 집계값이 있어야 합니다: " + time);
        }
    }
    /** 그 시간대에 값을 보낸 센서가 하나도 없을 때. */
    public static SpaceSeriesPoint empty(Instant time, boolean partial) {
        return new SpaceSeriesPoint(time, null, null, null, null, null, 0, partial);
    }
}