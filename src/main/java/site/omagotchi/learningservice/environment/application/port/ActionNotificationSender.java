package site.omagotchi.learningservice.environment.application.port;

import site.omagotchi.learningservice.environment.application.result.IotActionResult;
import site.omagotchi.learningservice.environment.domain.IotAction;
import site.omagotchi.learningservice.environment.domain.SensorDetection;
import site.omagotchi.learningservice.rule.domain.Operator;

import java.time.Instant;
import java.util.UUID;

public interface ActionNotificationSender {

    boolean send(ActionNotice notice);

    record ActionNotice(
            UUID recipientUserId,
            String location,
            String measurement,
            Double value,
            Operator operator,
            Double threshold,
            IotAction action,
            Instant confirmedAt,
            boolean simulated
    ){
        public static ActionNotice of(UUID recipientUserId, SensorDetection detection, IotAction action, IotActionResult result){
            return new ActionNotice(
                    recipientUserId,
                    detection.location(),
                    detection.measurement(),
                    detection.value(),
                    detection.operator(),
                    detection.threshold(),
                    action,
                    result.at(),
                    result.simulated()
            );
        }
    }
}
