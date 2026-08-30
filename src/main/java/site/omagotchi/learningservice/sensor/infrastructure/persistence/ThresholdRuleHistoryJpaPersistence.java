package site.omagotchi.learningservice.sensor.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.sensor.application.port.ThresholdRuleHistoryRepository;
import site.omagotchi.learningservice.sensor.domain.ThresholdRuleHistory;

@Repository
@RequiredArgsConstructor
public class ThresholdRuleHistoryJpaPersistence implements ThresholdRuleHistoryRepository {

    private final ThresholdRuleHistoryJpaRepository thresholdRuleHistoryJpaRepository;

    @Override
    public ThresholdRuleHistory save(ThresholdRuleHistory history) {
        return thresholdRuleHistoryJpaRepository.save(history);
    }
}
