package site.omagotchi.learningservice.sensor.application.port;

import lombok.Getter;

/** Persistence adapter가 application에 전달하는 저장 충돌 계약. */
@Getter
public class SensorPersistenceException extends RuntimeException {

    private final Reason reason;

    public SensorPersistenceException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public enum Reason {
        DEVICE_EUI_ALREADY_EXISTS,
        DEVICE_SPACE_NOT_FOUND,
        RULE_ALREADY_EXISTS,
        RULE_VERSION_CONFLICT
    }
}
