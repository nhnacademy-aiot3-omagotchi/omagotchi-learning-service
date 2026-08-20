package site.omagotchi.learningservice.environment.presentation.web.response;

import site.omagotchi.learningservice.environment.application.query.SensorEventItem;
import site.omagotchi.learningservice.environment.domain.*;

import java.time.Instant;
import java.util.Objects;

//SensorEvent
public record SensorEventResponse (
        String id,

        //SensorDetection
        SensorEventType type,
        String traceId,
        String deviceEui,
        String displayName,
        String location,
        String point,
        String measurement,
        Double value,
        String detail,
        Instant measuredAt,
        Instant receivedAt,

        //ActionOutcome
        IotAction action,
        String actionLabel,
        ActionStatus actionStatus,
        Instant actionConfirmedAt,
        Boolean actionSimulated,
        String actionError,
        Instant notifiedAt

){
    public static SensorEventResponse from(SensorEventItem item){
        SensorEvent event = item.event();
        SensorDetection detection = event.detection();
        ActionOutcome outcome = event.outcome();
        IotAction action = outcome.action();

        return new SensorEventResponse(
                event.id().toString(),
                detection.type(),
                detection.traceId(),
                detection.deviceEui(),
                item.deviceDisplayName(),
                detection.location(),
                detection.point(),
                detection.measurement(),
                detection.value(),
                detection.detail(),
                detection.measuredAt(),
                detection.receivedAt(),

                action,
                Objects.isNull(action) ? null : action.label(),
                outcome.status(),
                outcome.confirmedAt(),
                Objects.isNull(action) ? null : outcome.simulated(),
                outcome.error(),
                outcome.notifiedAt()
        );
    }
}
