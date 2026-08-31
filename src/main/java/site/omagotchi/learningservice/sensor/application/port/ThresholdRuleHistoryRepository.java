package site.omagotchi.learningservice.sensor.application.port;

import site.omagotchi.learningservice.sensor.domain.ThresholdRuleHistory;


public interface ThresholdRuleHistoryRepository {

    ThresholdRuleHistory save(ThresholdRuleHistory history);
}
