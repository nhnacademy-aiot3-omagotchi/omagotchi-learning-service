package site.omagotchi.learningservice.rule.application.port;

import site.omagotchi.learningservice.rule.domain.ThresholdRuleHistory;


public interface ThresholdRuleHistoryRepository {

    ThresholdRuleHistory save(ThresholdRuleHistory history);
}
