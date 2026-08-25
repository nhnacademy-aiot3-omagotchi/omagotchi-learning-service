package site.omagotchi.learningservice.rule.presentation.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import site.omagotchi.learningservice.rule.application.command.ApplySpaceThresholdCommand;
import site.omagotchi.learningservice.rule.application.command.ApplySpaceThresholdCommand.MetricCondition;
import site.omagotchi.learningservice.rule.domain.Operator;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 공간 단위 임계치 일괄 적용 요청.
 *
 * <p>spaceId 는 경로에서 받으므로 본문에 두지 않는다. baseVersion 도 받지 않는다 —
 * 대상 룰이 N 건이라 클라이언트가 N 개의 버전을 들 수 없고, 이 요청의 의도 자체가
 * 덮어쓰기다.</p>
 */
public record ApplySpaceThresholdRequest(

        @Valid
        @NotEmpty(message = "적용할 항목은 최소 하나여야 합니다.")
        List<MetricConditionRequest> rules
) {

    public ApplySpaceThresholdCommand toCommand(Long spaceId, UUID requesterId, String requestId) {
        List<MetricCondition> conditions = new ArrayList<>();
        for (MetricConditionRequest rule : rules) {
            conditions.add(new MetricCondition(rule.metric(), rule.operator(), rule.threshold()));
        }

        return new ApplySpaceThresholdCommand(spaceId, conditions, requesterId, requestId);
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
