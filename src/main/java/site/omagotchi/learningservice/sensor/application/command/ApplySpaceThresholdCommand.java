package site.omagotchi.learningservice.sensor.application.command;

import site.omagotchi.learningservice.sensor.domain.Operator;

import java.util.List;
import java.util.Locale;

public record ApplySpaceThresholdCommand (
        List<MetricCondition> conditions
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
