package site.omagotchi.learningservice.environment.presentation.message;

import site.omagotchi.learningservice.environment.domain.SensorDetection;
import site.omagotchi.learningservice.environment.domain.SensorEvent;
import site.omagotchi.learningservice.environment.domain.SensorEventType;
import site.omagotchi.learningservice.sensor.domain.Operator;

import java.time.Instant;

public record QualityEventMessage(
        String traceId,
        SensorEventType type,
        String location,
        String point,
        String deviceEui,
        String measurement,
        Double value,         // 래퍼 Double! MISSING(결측)은 값이 없어서 null 가능
        String detail,
        Operator operator,
        Double threshold,
        Instant measuredAt,
        Instant receivedAt
) {
    public SensorEvent toSensorEvent(){
        return SensorEvent.of(new SensorDetection(
                traceId,
                type,
                location,
                point,
                deviceEui,
                measurement,
                value,
                detail,
                operator,
                threshold,
                measuredAt,
                receivedAt
        ));
    }
}