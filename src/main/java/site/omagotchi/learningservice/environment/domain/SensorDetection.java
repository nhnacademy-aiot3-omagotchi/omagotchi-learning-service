package site.omagotchi.learningservice.environment.domain;

import java.time.Instant;
import java.util.Objects;

public record SensorDetection (
        String traceId,
        SensorEventType type,
        String location,
        String point,
        String deviceEui,
        String measurement,
        Double value,
        String detail,
        Instant measuredAt,
        Instant receivedAt
){
    /**
     * compact 생성자로 필드값 검증
     * </p>
     * type에 따라 다른 필드가 null일 수 있어 확정 필드 2개만 검증
     * TODO: 검증에 대한건 세윤님 파트 확인 필요할듯..
     */
    public SensorDetection{
        if(Objects.isNull(type)){
            throw new IllegalArgumentException("SensorEventType이 null입니다.");
        }

        if(Objects.isNull(receivedAt == null)){
            throw new IllegalArgumentException("receivedAt이 null입니다.");
        }
    }
}
