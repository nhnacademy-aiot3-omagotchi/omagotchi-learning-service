package site.omagotchi.learningservice.rule.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.rule.application.command.CreateThresholdRuleCommand;
import site.omagotchi.learningservice.rule.application.command.UpdateThresholdRuleCommand;
import site.omagotchi.learningservice.rule.application.port.SensorDeviceRepository;
import site.omagotchi.learningservice.rule.application.port.ThresholdRuleEventPublisher;
import site.omagotchi.learningservice.rule.application.port.ThresholdRuleHistoryRepository;
import site.omagotchi.learningservice.rule.application.port.ThresholdRuleRepository;
import site.omagotchi.learningservice.rule.application.result.ThresholdConditionResult;
import site.omagotchi.learningservice.rule.application.result.UpdateThresholdRuleResult;
import site.omagotchi.learningservice.rule.domain.ChangeType;
import site.omagotchi.learningservice.rule.domain.ThresholdRule;
import site.omagotchi.learningservice.rule.domain.ThresholdRuleHistory;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ThresholdRuleService {

    private final SensorDeviceRepository sensorDeviceRepository;
    private final ThresholdRuleRepository thresholdRuleRepository;
    private final ThresholdRuleHistoryRepository thresholdRuleHistoryRepository;
    private final ThresholdRuleEventPublisher eventPublisher;

    @Transactional
    public Long create(CreateThresholdRuleCommand command){
        ThresholdRule thresholdRule;
        try{
            thresholdRule = ThresholdRule.create(
                    command.deviceEui(),
                    command.metric(),
                    command.operator(),
                    command.threshold(),
                    command.requesterId()
            );
        }catch (IllegalArgumentException e){
            throw new BusinessException(RuleErrorCode.RULE_INVALID_CONDITION, e.getMessage(), e);
        }

        if (!sensorDeviceRepository.existsByDeviceEui(thresholdRule.getDeviceEui())) {
            throw new BusinessException(RuleErrorCode.DEVICE_NOT_FOUND);
        }

        if(thresholdRuleRepository.existsByDeviceEuiAndMetric(thresholdRule.getDeviceEui(), thresholdRule.getMetric())){
            throw new BusinessException(RuleErrorCode.RULE_ALREADY_EXISTS);
        }

        // 유니크 위반은 경계 안에서 RULE_ALREADY_EXISTS로 변환된다
        thresholdRuleRepository.save(thresholdRule);

        thresholdRuleHistoryRepository.save(ThresholdRuleHistory.record(
                thresholdRule,
                ChangeType.CREATED,
                command.requesterId(),
                command.requestId()
        ));

        eventPublisher.publishThresholdRuleChanged(thresholdRule);

        log.info("임계치 룰 생성: id={}", thresholdRule.getId());
        return thresholdRule.getId();
    }

    @Transactional
    public UpdateThresholdRuleResult update(UpdateThresholdRuleCommand command){
        ThresholdRule thresholdRule = thresholdRuleRepository.findById(command.ruleId()).orElseThrow(
                () -> new BusinessException(RuleErrorCode.RULE_NOT_FOUND));

        if(!thresholdRule.getVersion().equals(command.baseVersion())){
            throw new BusinessException(RuleErrorCode.RULE_VERSION_CONFLICT);
        }

        boolean changed;
        try{
            changed = thresholdRule.changeCondition(
                    command.operator(), command.threshold(), command.requesterId());
        }catch (IllegalArgumentException e){
            throw new BusinessException(RuleErrorCode.RULE_INVALID_CONDITION, e.getMessage(), e);
        }

        if(!changed){
            return new UpdateThresholdRuleResult(false, thresholdRule.getVersion());
        }

        // 낙관적 락 충돌은 경계 안에서 RULE_VERSION_CONFLICT로 변환된다
        thresholdRuleRepository.update(thresholdRule);

        thresholdRuleHistoryRepository.save(ThresholdRuleHistory.record(
                thresholdRule,
                ChangeType.UPDATED,
                command.requesterId(),
                command.requestId()
        ));

        eventPublisher.publishThresholdRuleChanged(thresholdRule);

        log.info("임계치 룰 변경 id={}, v{}", thresholdRule.getId(), thresholdRule.getVersion());
        return new UpdateThresholdRuleResult(true, thresholdRule.getVersion());

    }

    public Optional<ThresholdConditionResult> findCondition(String deviceEui, String metric) {

        if (Objects.isNull(deviceEui) || Objects.isNull(metric) || metric.isBlank()) {
            return Optional.empty();
        }

        String normalized = metric.trim().toLowerCase(Locale.ROOT);
        Optional<ThresholdRule> found = thresholdRuleRepository.findByDeviceEuiAndMetric(deviceEui, normalized);

        if (found.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(ThresholdConditionResult.from(found.get()));
    }

    public List<ThresholdRule> readAll(){
        return thresholdRuleRepository.findAll();
    }

}


