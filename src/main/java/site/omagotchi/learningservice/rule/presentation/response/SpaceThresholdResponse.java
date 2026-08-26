package site.omagotchi.learningservice.rule.presentation.response;

import site.omagotchi.learningservice.rule.application.result.SpaceThresholdResult;
import site.omagotchi.learningservice.rule.application.result.SpaceThresholdResult.MetricThresholdResult;
import site.omagotchi.learningservice.rule.domain.Operator;

import java.util.ArrayList;
import java.util.List;

/**
 * 공간 하나의 현재 임계치.
 *
 * <p>공간 이름을 담지 않는 것이 의도다 — 화면은 이미 {@code GET /api/v1/spaces} 를
 * 부르므로 spaceId 로 조인하면 되고, rule 이 space Feature 에 의존하지 않게 된다.</p>
 */
public record SpaceThresholdResponse(
        Long spaceId,
        int deviceCount,
        List<MetricThresholdResponse> metrics
) {

    public static SpaceThresholdResponse from(SpaceThresholdResult result) {
        List<MetricThresholdResponse> metrics = new ArrayList<>();
        for (MetricThresholdResult metric : result.metrics()) {
            metrics.add(MetricThresholdResponse.from(metric));
        }

        return new SpaceThresholdResponse(result.spaceId(), result.deviceCount(), metrics);
    }

    /**
     * @param ruleCount 이 공간에서 이 metric 룰을 가진 기기 수. deviceCount 보다 작으면
     *                  일부 기기가 이 항목을 감시하지 않는다는 뜻이다
     * @param mixed 기기마다 조건이 다르면 true. 화면은 이때 대표값을 보여주되 경고해야 한다
     */
    public record MetricThresholdResponse(
            String metric,
            Operator operator,
            Double threshold,
            int ruleCount,
            boolean mixed
    ) {

        static MetricThresholdResponse from(MetricThresholdResult result) {
            return new MetricThresholdResponse(
                    result.metric(),
                    result.operator(),
                    result.threshold(),
                    result.ruleCount(),
                    result.mixed()
            );
        }
    }
}
