package site.omagotchi.learningservice.rule.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.rule.application.command.ApplySpaceThresholdCommand;
import site.omagotchi.learningservice.rule.application.command.ApplySpaceThresholdCommand.MetricCondition;
import site.omagotchi.learningservice.rule.application.command.CreateThresholdRuleCommand;
import site.omagotchi.learningservice.rule.application.command.UpdateThresholdRuleCommand;
import site.omagotchi.learningservice.rule.application.port.SensorDeviceRepository;
import site.omagotchi.learningservice.rule.application.port.ThresholdRuleEventPublisher;
import site.omagotchi.learningservice.rule.application.port.ThresholdRuleHistoryRepository;
import site.omagotchi.learningservice.rule.application.port.ThresholdRuleRepository;
import site.omagotchi.learningservice.rule.application.result.ApplySpaceThresholdResult;
import site.omagotchi.learningservice.rule.application.result.SpaceThresholdResult;
import site.omagotchi.learningservice.rule.application.result.SpaceThresholdResult.MetricThresholdResult;
import site.omagotchi.learningservice.rule.application.result.ThresholdConditionResult;
import site.omagotchi.learningservice.rule.application.result.UpdateThresholdRuleResult;
import site.omagotchi.learningservice.rule.domain.ChangeType;
import site.omagotchi.learningservice.rule.domain.SensorDevice;
import site.omagotchi.learningservice.rule.domain.ThresholdRule;
import site.omagotchi.learningservice.rule.domain.ThresholdRuleHistory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    /** 공간별 현재 임계치. 화면이 이걸로 입력 폼을 그린다 */
    public List<SpaceThresholdResult> findAllBySpace() {
        Map<Long, List<SensorDevice>> devicesBySpace = new LinkedHashMap<>();

        for (SensorDevice device : sensorDeviceRepository.findAllWithSpace()) {
            devicesBySpace
                    .computeIfAbsent(device.getSpaceId(), key -> new ArrayList<>())
                    .add(device);
        }

        List<SpaceThresholdResult> results = new ArrayList<>();
        for (Map.Entry<Long, List<SensorDevice>> entry : devicesBySpace.entrySet()) {
            results.add(summarize(entry.getKey(), entry.getValue()));
        }

        return results;
    }

    /**
     * 공간 안 모든 기기의 임계치를 한 번에 맞춘다.
     *
     * <p>룰이 없는 기기에는 만들지 않는다 — 그 기기가 이 metric 을 측정하는지 서버가
     * 알 수 없다. 온도계에 co2 룰이 붙는 것을 막기 위한 의도적 제약이며, 건너뛴 수를
     * missing 으로 돌려주어 화면이 드러내게 한다.</p>
     *
     * <p>N 건의 이력이 같은 requestId 를 공유한다. 나중에 "한 번의 공간 변경"으로
     * 묶어 볼 수 있는 유일한 단서다.</p>
     *
     * <p>baseVersion 을 받지 않는다. "이 공간의 co2 를 1000 으로"라는 의도 자체가
     * 덮어쓰기이고, 대상이 N 건이라 클라이언트가 N 개의 버전을 들 수 없다.</p>
     */
    @Transactional
    public ApplySpaceThresholdResult applyToSpace(ApplySpaceThresholdCommand command) {
        List<SensorDevice> devices = sensorDeviceRepository.findBySpaceId(command.spaceId());

        if (devices.isEmpty()) {
            throw new BusinessException(RuleErrorCode.SPACE_HAS_NO_DEVICE);
        }

        Map<String, ThresholdRule> rulesByKey = indexRules(devices);

        int applied = 0;
        int unchanged = 0;
        int missing = 0;

        for (SensorDevice device : devices) {
            for (MetricCondition condition : command.conditions()) {

                ThresholdRule rule = rulesByKey.get(
                        ruleKey(device.getDeviceEui(), condition.normalizedMetric()));

                if (Objects.isNull(rule)) {
                    missing++;
                    continue;
                }

                boolean changed;
                try {
                    changed = rule.changeCondition(
                            condition.operator(), condition.threshold(), command.requesterId());
                } catch (IllegalArgumentException e) {
                    throw new BusinessException(RuleErrorCode.RULE_INVALID_CONDITION, e.getMessage(), e);
                }

                if (!changed) {
                    unchanged++;
                    continue;
                }

                thresholdRuleRepository.update(rule);

                thresholdRuleHistoryRepository.save(ThresholdRuleHistory.record(
                        rule, ChangeType.UPDATED, command.requesterId(), command.requestId()));

                eventPublisher.publishThresholdRuleChanged(rule);
                applied++;
            }
        }

        log.info("공간 임계치 일괄 적용: spaceId={}, 적용={}, 동일={}, 룰없음={}",
                command.spaceId(), applied, unchanged, missing);

        return new ApplySpaceThresholdResult(
                command.spaceId(), devices.size(), applied, unchanged, missing);
    }

    /** 공간 하나의 metric 별 요약. 기기 수만큼 쿼리를 날리지 않는다 */
    private SpaceThresholdResult summarize(Long spaceId, List<SensorDevice> devices) {
        Map<String, List<ThresholdRule>> rulesByMetric = new LinkedHashMap<>();
        for (ThresholdRule rule : thresholdRuleRepository.findByDeviceEuiIn(deviceEuisOf(devices))) {
            rulesByMetric
                    .computeIfAbsent(rule.getMetric(), key -> new ArrayList<>())
                    .add(rule);
        }

        List<MetricThresholdResult> metrics = new ArrayList<>();
        for (Map.Entry<String, List<ThresholdRule>> entry : rulesByMetric.entrySet()) {
            metrics.add(MetricThresholdResult.of(entry.getKey(), entry.getValue()));
        }

        return new SpaceThresholdResult(spaceId, devices.size(), metrics);
    }

    /**
     * 공간의 룰을 한 번에 읽어 (deviceEui, metric) 으로 색인한다.
     *
     * <p>반복문 안에서 단건 조회하면 기기 수 × 항목 수만큼 쿼리가 나간다 —
     * 센서 10대에 3항목이면 30회다. rule-service 의 InMemoryRuleCache 와 같은
     * 키 모양을 쓴다.</p>
     */
    private Map<String, ThresholdRule> indexRules(List<SensorDevice> devices) {
        Map<String, ThresholdRule> rulesByKey = new HashMap<>();

        for (ThresholdRule rule : thresholdRuleRepository.findByDeviceEuiIn(deviceEuisOf(devices))) {
            rulesByKey.put(ruleKey(rule.getDeviceEui(), rule.getMetric()), rule);
        }

        return rulesByKey;
    }

    private static List<String> deviceEuisOf(List<SensorDevice> devices) {
        List<String> deviceEuis = new ArrayList<>();
        for (SensorDevice device : devices) {
            deviceEuis.add(device.getDeviceEui());
        }
        return deviceEuis;
    }

    private static String ruleKey(String deviceEui, String metric) {
        return deviceEui + ":" + metric;
    }

}
