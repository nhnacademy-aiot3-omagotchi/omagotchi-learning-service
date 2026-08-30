package site.omagotchi.learningservice.sensor.application.result;

import site.omagotchi.learningservice.sensor.domain.Operator;
import site.omagotchi.learningservice.sensor.domain.ThresholdRule;

import java.util.List;

public record SpaceThresholdResult(
        Long spaceId,
        int deviceCount,
        List<MetricThresholdResult> metrics
) {
    public record MetricThresholdResult(
            String metric,
            Operator operator,
            Double threshold,
            int ruleCount,
            boolean mixed
    ){
        public static MetricThresholdResult of(String metric, List<ThresholdRule> rules){
            ThresholdRule majority = rules.getFirst();
            int best = 0;

            for(ThresholdRule candidate : rules){
                int count = countSameAs(rules, candidate);
                if(count > best){
                    best = count;
                    majority = candidate;
                }
            }

            int matching = countSameAs(rules, majority);

            return new MetricThresholdResult(
                    metric,
                    majority.getOperator(),
                    majority.getThreshold(),
                    rules.size(),
                    matching < rules.size()
            );
        }
        private static int countSameAs(List<ThresholdRule> rules, ThresholdRule target){
            int count = 0;
            for(ThresholdRule rule : rules){
                if(!differsFrom(rule, target)){
                    count++;
                }
            }
            return count;
        }
        private static boolean differsFrom(ThresholdRule rule, ThresholdRule other){
            if(rule.getOperator() != other.getOperator()){
                return true;
            }

            return rule.getThreshold().compareTo(other.getThreshold()) != 0;
        }
    }


}
