package site.omagotchi.learningservice.environment.presentation.message;

import site.omagotchi.learningservice.environment.domain.SensorEventType;

import java.time.Instant;

public record QualityEventMessage(
        int version,          // 스키마 버전. 지금은 1로 고정 시작
        String traceId,
        SensorEventType type,

        String location,
        String point,
        String deviceEui,
        String measurement,

        Double value,         // 래퍼 Double! MISSING(결측)은 값이 없어서 null 가능
        Instant measuredAt,
        Instant receivedAt,

        String detail          // 판정 사유 (예: "co2 4200 > 임계 1000")
) { }