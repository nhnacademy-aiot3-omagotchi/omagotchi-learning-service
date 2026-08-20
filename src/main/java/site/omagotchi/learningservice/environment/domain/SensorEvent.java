package site.omagotchi.learningservice.environment.domain;

import java.util.UUID;

public record SensorEvent(
        UUID id,
        SensorDetection detection,
        ActionOutcome outcome
) {
    public static SensorEvent of(SensorDetection detection){
        return new SensorEvent(UUID.randomUUID(), detection, ActionOutcome.none());
    }

    public SensorEvent withOutcome(ActionOutcome outCome){
        return new SensorEvent(id, detection, outCome);
    }
}
