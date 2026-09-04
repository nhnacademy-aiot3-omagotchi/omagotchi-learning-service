package site.omagotchi.learningservice.sensor.presentation.response;

import site.omagotchi.learningservice.sensor.application.result.SpaceEnvironmentResult;

import java.time.Instant;

/**
 * 공간 하나의 현재 환경.
 *
 * <p>공간 이름을 담지 않는 것이 의도다 — 화면은 이미 {@code GET /api/v1/spaces} 를 부르므로
 * spaceId 로 조인하면 된다. ({@code SpaceThresholdResponse} 와 같은 규칙)</p>
 */
public record SpaceEnvironmentResponse(
        Long spaceId,
        Double co2,
        Double temperature,
        Double humidity,
        Instant measuredAt,
        int deviceCount
) {

    public static SpaceEnvironmentResponse from(SpaceEnvironmentResult result) {
        return new SpaceEnvironmentResponse(
                result.spaceId(),
                result.co2(),
                result.temperature(),
                result.humidity(),
                result.measuredAt(),
                result.deviceCount()
        );
    }
}
