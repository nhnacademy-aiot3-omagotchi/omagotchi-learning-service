package site.omagotchi.learningservice.sensor.application.result;

import java.time.Instant;

/**
 * 공간의 현재 환경 측정 없는 항목은 null
 *
 * <p>deviceCount 는 그 공간에 배치된 운영 중인 센서 수다. 0이면 값이 없는 이유가
 * "아직 안 들어옴"이 아니라 "센서가 없음"이라는 뜻이라, 화면이 둘을 구분해 말할 수 있다.</p>
 */
public record SpaceEnvironmentResult (
        Long spaceId,
        Double co2,
        Double temperature,
        Double humidity,
        Instant measuredAt,
        int deviceCount
){ }
