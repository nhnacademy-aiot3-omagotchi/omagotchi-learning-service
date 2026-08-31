package site.omagotchi.learningservice.environment.application;

import site.omagotchi.learningservice.environment.domain.IotAction;
import site.omagotchi.learningservice.sensor.domain.Operator;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * 어떤 측정값이 어느 방향으로 룰 히트됐을때 취할 조치 반환.
 * 매핑 반환 클래스
 */
public final class IotActionPolicy {

    public static Optional<IotAction> resolve(String measurement, Operator operator){
        if(Objects.isNull(measurement) || Objects.isNull(operator)){
            return Optional.empty();
        }

        String metric = measurement.toLowerCase(Locale.ROOT);
        boolean above = isAbove(operator);

        return Optional.ofNullable(switch (metric){
            case "co2" -> above ? IotAction.VENTILATE : null;
            case "temperature" -> above ? IotAction.COOL : IotAction.HEAT;
            case "humidity" -> above ? IotAction.DEHUMIDIFY : IotAction.HUMIDIFY;
            default -> null;
        });
    }

    private static boolean isAbove(Operator operator){
        return operator == Operator.GT || operator == Operator.GTE;
    }
}