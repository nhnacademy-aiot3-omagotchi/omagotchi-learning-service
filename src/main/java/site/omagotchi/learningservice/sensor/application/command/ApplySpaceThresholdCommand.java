package site.omagotchi.learningservice.sensor.application.command;

import site.omagotchi.learningservice.sensor.domain.Operator;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public record ApplySpaceThresholdCommand (
        Long spaceId,
        List<MetricCondition> conditions,
        UUID requesterId,
        String requestId
){
    public record MetricCondition(
            String metric,
            Operator operator,
            Double threshold
    ){
        public String normalizedMetric(){
            return metric == null ? null : metric.trim().toLowerCase(Locale.ROOT);
        }
    }

}
