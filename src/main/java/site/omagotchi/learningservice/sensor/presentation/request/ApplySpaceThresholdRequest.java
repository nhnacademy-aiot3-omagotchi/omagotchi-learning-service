package site.omagotchi.learningservice.sensor.presentation.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import site.omagotchi.learningservice.sensor.application.command.ApplySpaceThresholdCommand;
import site.omagotchi.learningservice.sensor.application.command.ApplySpaceThresholdCommand.MetricCondition;
import site.omagotchi.learningservice.sensor.domain.Operator;

import java.util.ArrayList;
import java.util.List;

/**
 * 공간 단위 임계치 일괄 적용 요청.
 *
 * <p>spaceId 는 경로에서 받으므로 본문에 두지 않는다. baseVersion 도 받지 않는다 —
 * 대상 룰이 N 건이라 클라이언트가 N 개의 버전을 들 수 없고, 이 요청의 의도 자체가
 * 덮어쓰기다.</p>
 */
public record ApplySpaceThresholdRequest(

        @Valid
        @NotNull
        @Size(min = 3, max = 3, message = "CO2, 온도, 습도 임계값을 모두 입력해야 합니다.")
        List<MetricConditionRequest> rules
) {

    public ApplySpaceThresholdCommand toCommand() {
        List<MetricCondition> conditions = new ArrayList<>();
        for (MetricConditionRequest rule : rules) {
            conditions.add(new MetricCondition(rule.metric(), rule.operator(), rule.threshold()));
        }

        return new ApplySpaceThresholdCommand(conditions);
    }

    public record MetricConditionRequest(

            @NotBlank
            @Size(max = 32, message = "metric은 32자를 넘을 수 없습니다.")
            String metric,

            @NotNull
            Operator operator,

            @NotNull
            @DecimalMin(value = "-1e9")
            @DecimalMax(value = "1e9")
            Double threshold
    ) {
    }
}
