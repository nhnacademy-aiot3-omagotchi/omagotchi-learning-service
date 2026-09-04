package site.omagotchi.learningservice.sensor.application.query;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** 여러 공간을 한번에 조회 쿼리 */
public record SpaceEnvironmentQuery(
        Set<String> deviceEuis,
        List<String> measurement,
        Instant from,
        Instant to
) { }
