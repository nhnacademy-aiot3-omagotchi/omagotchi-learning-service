package site.omagotchi.learningservice.rule.infrastructure.persistence.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.rule.application.port.ThresholdRuleHistoryRepository;
import site.omagotchi.learningservice.rule.domain.ThresholdRuleHistory;

@Repository
@RequiredArgsConstructor
public class ThresholdRuleHistoryJpaPersistence implements ThresholdRuleHistoryRepository {

    private final ThresholdRuleHistoryJpaRepository thresholdRuleHistoryJpaRepository;

    @Override
    public ThresholdRuleHistory save(ThresholdRuleHistory history) {
        return thresholdRuleHistoryJpaRepository.save(history);
    }
}
