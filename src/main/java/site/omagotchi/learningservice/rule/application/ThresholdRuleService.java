package site.omagotchi.learningservice.rule.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.rule.application.command.CreateThresholdRuleCommand;
import site.omagotchi.learningservice.rule.application.command.UpdateThresholdRuleCommand;
import site.omagotchi.learningservice.rule.application.result.UpdateThresholdRuleResult;
import site.omagotchi.learningservice.rule.domain.ChangeType;
import site.omagotchi.learningservice.rule.domain.ThresholdRule;
import site.omagotchi.learningservice.rule.domain.ThresholdRuleHistory;
import site.omagotchi.learningservice.rule.infrastructure.SensorDeviceRepository;
import site.omagotchi.learningservice.rule.infrastructure.ThresholdRuleHistoryRepository;
import site.omagotchi.learningservice.rule.infrastructure.ThresholdRuleRepository;

import java.util.List;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ThresholdRuleService {

    private final SensorDeviceRepository sensorDeviceRepository;
    private final ThresholdRuleRepository thresholdRuleRepository;
    private final ThresholdRuleHistoryRepository thresholdRuleHistoryRepository;

    @Transactional
    public Long create(CreateThresholdRuleCommand command){
        if(!sensorDeviceRepository.existsById(command.deviceEui())){
            throw new IllegalArgumentException();
        }

        ThresholdRule thresholdRule = ThresholdRule.create(
                command.deviceEui(),
                command.metric(),
                command.operator(),
                command.threshold(),
                command.requesterId()
        );

        thresholdRuleRepository.save(thresholdRule);
        thresholdRuleRepository.flush();

        thresholdRuleHistoryRepository.save(ThresholdRuleHistory.record(
                thresholdRule,
                ChangeType.CREATED,
                command.requesterId(),
                command.requestId()
        ));


        log.info("임계치 룰 생성: id={}", thresholdRule.getId());
        return thresholdRule.getId();
    }

    @Transactional
    public UpdateThresholdRuleResult update(UpdateThresholdRuleCommand command){
        ThresholdRule thresholdRule = thresholdRuleRepository.findById(command.ruleId()).orElseThrow(() -> new IllegalArgumentException());

        boolean changed = thresholdRule.changeCondition(
                command.operator(), command.threshold(), command.requesterId());

        if(!changed){
            return new UpdateThresholdRuleResult(false, thresholdRule.getVersion());
        }
        thresholdRuleRepository.flush();

        thresholdRuleHistoryRepository.save(ThresholdRuleHistory.record(
                thresholdRule,
                ChangeType.UPDATED,
                command.requesterId(),
                command.requestId()
        ));

        log.info("임계치 룰 변경 id={}, v{}", thresholdRule.getId(), thresholdRule.getVersion());
        return new UpdateThresholdRuleResult(true, thresholdRule.getVersion());
    }

    public List<ThresholdRule> readAll(){
        return thresholdRuleRepository.findAll();
    }

}


