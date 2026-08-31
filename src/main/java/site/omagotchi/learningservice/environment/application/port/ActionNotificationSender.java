package site.omagotchi.learningservice.environment.application.port;

import site.omagotchi.learningservice.environment.application.result.IotActionResult;
import site.omagotchi.learningservice.environment.domain.IotAction;
import site.omagotchi.learningservice.environment.domain.SensorDetection;
import site.omagotchi.learningservice.sensor.domain.Operator;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public interface ActionNotificationSender {

    /**
     * 한 사람에게 보내고 <b>{@code timeout} 안에 돌아온다.</b>
     *
     * <p>호출부가 남은 예산을 넘겨 준다. 발송이 늦어져도 호출 스레드(MQ 리스너)가 그보다
     * 오래 묶이지 않는 것이 이 계약의 핵심이다.</p>
     *
     * @return 실제로 보냈으면 {@code true}, 받을 수 없는 사용자라 건너뛰었으면 {@code false}
     */
    boolean send(ActionNotice notice, Duration timeout);

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
