package site.omagotchi.learningservice.rule.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.rule.application.command.CreateThresholdRuleCommand;
import site.omagotchi.learningservice.rule.application.command.UpdateThresholdRuleCommand;
import site.omagotchi.learningservice.rule.application.result.UpdateThresholdRuleResult;
import site.omagotchi.learningservice.rule.domain.*;
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

        if (!sensorDeviceRepository.existsById(command.deviceEui())) {
            throw new BusinessException(RuleErrorCode.DEVICE_NOT_FOUND);
        }
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

        if(thresholdRuleRepository.existsByDeviceEuiAndMetric(command.deviceEui(), command.metric())){
            throw new BusinessException(RuleErrorCode.RULE_ALREADY_EXISTS);
        }

        try{
            thresholdRuleRepository.save(thresholdRule);
            thresholdRuleRepository.flush();
        }catch (DataIntegrityViolationException e){
            throw new BusinessException(RuleErrorCode.RULE_ALREADY_EXISTS, e.getMessage(), e);
        }


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

        try{
            thresholdRuleRepository.flush();
        }catch (ObjectOptimisticLockingFailureException e){
            throw new BusinessException(RuleErrorCode.RULE_VERSION_CONFLICT, e.getMessage(), e);
        }

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


