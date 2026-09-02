package site.omagotchi.learningservice.sensor.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.sensor.application.command.ApplySpaceThresholdCommand;
import site.omagotchi.learningservice.sensor.application.command.ApplySpaceThresholdCommand.MetricCondition;
import site.omagotchi.learningservice.sensor.application.command.CreateThresholdRuleCommand;
import site.omagotchi.learningservice.sensor.application.command.UpdateThresholdRuleCommand;
import site.omagotchi.learningservice.sensor.application.port.SensorDeviceRepository;
import site.omagotchi.learningservice.sensor.application.port.SensorPersistenceException;
import site.omagotchi.learningservice.sensor.application.port.ThresholdRuleEventPublisher;
import site.omagotchi.learningservice.sensor.application.port.ThresholdRuleHistoryRepository;
import site.omagotchi.learningservice.sensor.application.port.ThresholdRuleRepository;
import site.omagotchi.learningservice.sensor.application.result.ApplySpaceThresholdResult;
import site.omagotchi.learningservice.sensor.application.result.SpaceThresholdResult;
import site.omagotchi.learningservice.sensor.application.result.SpaceThresholdResult.MetricThresholdResult;
import site.omagotchi.learningservice.sensor.application.result.UpdateThresholdRuleResult;
import site.omagotchi.learningservice.sensor.domain.*;
import site.omagotchi.learningservice.space.application.SpaceCohortQueryService;

import java.util.*;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ThresholdRuleService {

    private static final List<RequiredMetric> REQUIRED_METRICS = List.of(
            new RequiredMetric("co2", Operator.GTE, 1000.0),
            new RequiredMetric("temperature", Operator.GTE, 28.0),
            new RequiredMetric("humidity", Operator.GTE, 70.0)
    );

    private final SensorDeviceRepository sensorDeviceRepository;
    private final ThresholdRuleRepository thresholdRuleRepository;
    private final ThresholdRuleHistoryRepository thresholdRuleHistoryRepository;
    private final ThresholdRuleEventPublisher eventPublisher;
    private final CohortAccessService cohortAccessService;
    private final SpaceCohortQueryService spaceCohortQueryService;

    @Transactional
    public Long create(
            Long cohortId,
            UUID requesterId,
            String requestId,
            CreateThresholdRuleCommand command
    ) {
        cohortAccessService.requireManager(cohortId, requesterId);
        requireDeviceInCohort(command.deviceEui(), cohortId, SensorErrorCode.DEVICE_NOT_FOUND);

        ThresholdRule thresholdRule = createRule(
                command.deviceEui(),
                command.metric(),
                command.operator(),
                command.threshold(),
                requesterId,
                requestId
        );

        log.info("임계치 룰 생성: id={}", thresholdRule.getId());
        return thresholdRule.getId();
    }

    @Transactional
    public UpdateThresholdRuleResult update(
            Long cohortId,
            UUID requesterId,
            String requestId,
            Long ruleId,
            UpdateThresholdRuleCommand command
    ) {
        cohortAccessService.requireManager(cohortId, requesterId);
        ThresholdRule thresholdRule = thresholdRuleRepository.findById(ruleId).orElseThrow(
                () -> new BusinessException(SensorErrorCode.RULE_NOT_FOUND));
        requireDeviceInCohort(
                thresholdRule.getDeviceEui(),
                cohortId,
                SensorErrorCode.RULE_NOT_FOUND
        );

        if (!thresholdRule.getVersion().equals(command.baseVersion())) {
            throw new BusinessException(SensorErrorCode.RULE_VERSION_CONFLICT);
        }

        boolean changed;
        try {
            changed = thresholdRule.changeCondition(
                    command.operator(), command.threshold(), requesterId);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(SensorErrorCode.RULE_INVALID_CONDITION, e.getMessage(), e);
        }

        if (!changed) {
            return new UpdateThresholdRuleResult(false, thresholdRule.getVersion());
        }

        // 낙관적 락 충돌은 경계 안에서 RULE_VERSION_CONFLICT로 변환된다
        updateRule(thresholdRule);

        thresholdRuleHistoryRepository.save(ThresholdRuleHistory.record(
                thresholdRule,
                ChangeType.UPDATED,
                requesterId,
                requestId
        ));

        eventPublisher.publishThresholdRuleChanged(thresholdRule);

        log.info("임계치 룰 변경 id={}, v{}", thresholdRule.getId(), thresholdRule.getVersion());
        return new UpdateThresholdRuleResult(true, thresholdRule.getVersion());

    }

    /**
     * Rule Engine 적재용 — 기수를 가리지 않는다.
     *
     * <p>회수된 기기와 <b>주인 없는 기기</b>의 룰을 모두 뺀다. {@code active} 플래그만
     * 보면 기수 종료 정리가 비활성화에 실패했을 때 고아 센서의 룰이 그대로 나간다 —
     * 그 정리는 되돌릴 수단이 없는 한 번뿐인 훅이라, 실패를 룰 발화까지 번지게 두면
     * 복구할 방법이 없어진다. 공간이 소프트 삭제되어 고아가 된 경로도 같이 닫힌다.</p>
     *
     * <p>인계로 공간이 다시 붙는 순간 룰도 저절로 돌아온다 — 별도 복구 절차가 없다.</p>
     *
     * <p>rule-service는 인메모리 캐시를 주기적으로 다시 적재하므로 반영까지 최대 한
     * 주기가 걸린다.</p>
     */
    public List<ThresholdRule> readAllForRuleEngine() {
        List<Long> assignedSpaceIds = spaceCohortQueryService.findAllAssignedSpaceIds();
        List<SensorDevice> devices = sensorDeviceRepository.findActiveBySpaceIds(assignedSpaceIds);
        if (devices.isEmpty()) {
            return List.of();
        }
        return thresholdRuleRepository.findByDeviceEuiIn(deviceEuisOf(devices));
    }

    /** 임계치는 rule-service 판정을 바꾸는 운영 설정이다. 사용자 화면에 쓰이지 않으므로 매니저만. */
    public List<ThresholdRule> findAllByCohort(Long cohortId, UUID requesterId) {
        cohortAccessService.requireManager(cohortId, requesterId);
        List<SensorDevice> devices = findDevicesByCohortId(cohortId);
        if (devices.isEmpty()) {
            return List.of();
        }
        return thresholdRuleRepository.findByDeviceEuiIn(deviceEuisOf(devices));
    }

    /** 공간별 현재 임계치. 화면이 이걸로 입력 폼을 그린다 — 입력 폼이므로 매니저만. */
    public List<SpaceThresholdResult> findAllBySpace(Long cohortId, UUID requesterId) {
        cohortAccessService.requireManager(cohortId, requesterId);
        Map<Long, List<SensorDevice>> devicesBySpace = new LinkedHashMap<>();

        List<SensorDevice> devices = findDevicesByCohortId(cohortId);
        for (SensorDevice device : devices) {
            devicesBySpace
                    .computeIfAbsent(device.getSpaceId(), key -> new ArrayList<>())
                    .add(device);
        }

        Map<String, List<ThresholdRule>> rulesByDeviceEui = indexRulesByDeviceEui(devices);
        List<SpaceThresholdResult> results = new ArrayList<>();
        for (Map.Entry<Long, List<SensorDevice>> entry : devicesBySpace.entrySet()) {
            results.add(summarize(
                    entry.getKey(),
                    entry.getValue(),
                    rulesByDeviceEui
            ));
        }

        return results;
    }

    /**
     * 공간 안 모든 기기의 임계치를 한 번에 맞춘다.
     *
     * <p>요청 본문의 항목은 관리자가 이 공간에 적용하겠다고 명시한 metric 이다. 따라서
     * 기존 룰은 갱신하고 없는 룰은 만든다. 이전에는 없는 룰을 건너뛰어, 센서가 등록돼도
     * 임계값을 설정할 수 없는 상태가 남았다.</p>
     *
     * <p>N 건의 이력이 같은 requestId 를 공유한다. 나중에 "한 번의 공간 변경"으로
     * 묶어 볼 수 있는 유일한 단서다.</p>
     *
     * <p>baseVersion 을 받지 않는다. "이 공간의 co2 를 1000 으로"라는 의도 자체가
     * 덮어쓰기이고, 대상이 N 건이라 클라이언트가 N 개의 버전을 들 수 없다.</p>
     */
    @Transactional
    public ApplySpaceThresholdResult applyToSpace(
            Long cohortId,
            UUID requesterId,
            String requestId,
            Long spaceId,
            ApplySpaceThresholdCommand command
    ) {
        cohortAccessService.requireManager(cohortId, requesterId);
        requireSpaceInCohort(spaceId, cohortId);
        List<MetricCondition> requiredConditions = requireAllMetrics(command.conditions());
        List<SensorDevice> devices = sensorDeviceRepository.findBySpaceIds(List.of(spaceId));

        if (devices.isEmpty()) {
            throw new BusinessException(SensorErrorCode.SPACE_HAS_NO_DEVICE);
        }

        Map<String, ThresholdRule> rulesByKey = indexRules(devices);

        int created = 0;
        int applied = 0;
        int unchanged = 0;

        for (SensorDevice device : devices) {
            for (MetricCondition condition : requiredConditions) {

                ThresholdRule rule = rulesByKey.get(
                        ruleKey(device.getDeviceEui(), condition.normalizedMetric()));

                if (Objects.isNull(rule)) {
                    ThresholdRule createdRule = createRule(
                            device.getDeviceEui(),
                            condition.normalizedMetric(),
                            condition.operator(),
                            condition.threshold(),
                            requesterId,
                            requestId
                    );
                    rulesByKey.put(ruleKey(createdRule.getDeviceEui(), createdRule.getMetric()), createdRule);
                    created++;
                    continue;
                }

                boolean changed;
                try {
                    changed = rule.changeCondition(
                            condition.operator(), condition.threshold(), requesterId);
                } catch (IllegalArgumentException e) {
                    throw new BusinessException(SensorErrorCode.RULE_INVALID_CONDITION, e.getMessage(), e);
                }

                if (!changed) {
                    unchanged++;
                    continue;
                }

                updateRule(rule);

                thresholdRuleHistoryRepository.save(ThresholdRuleHistory.record(
                        rule, ChangeType.UPDATED, requesterId, requestId));

                eventPublisher.publishThresholdRuleChanged(rule);
                applied++;
            }
        }

        log.info("공간 임계치 일괄 적용: spaceId={}, 생성={}, 적용={}, 동일={}",
                spaceId, created, applied, unchanged);

        return new ApplySpaceThresholdResult(
                spaceId, devices.size(), created, applied, unchanged, 0);
    }

    /** 센서 등록·이동·인계 시 목적지 공간의 대표값 또는 서버 기본값으로 필수 룰을 맞춘다. */
    @Transactional
    public int synchronizeRequiredRulesForDevice(
            SensorDevice targetDevice,
            UUID requesterId,
            String requestId
    ) {
        List<SensorDevice> peers = sensorDeviceRepository.findBySpaceIds(List.of(targetDevice.getSpaceId()))
                .stream()
                .filter(device -> !device.getDeviceEui().equals(targetDevice.getDeviceEui()))
                .toList();

        LinkedHashSet<String> uniqueDeviceEuis = new LinkedHashSet<>(deviceEuisOf(peers));
        uniqueDeviceEuis.add(targetDevice.getDeviceEui());
        List<String> deviceEuis = List.copyOf(uniqueDeviceEuis);
        List<ThresholdRule> allRules = thresholdRuleRepository.findByDeviceEuiIn(deviceEuis);

        Map<String, ThresholdRule> targetRules = new HashMap<>();
        Map<String, List<ThresholdRule>> peerRulesByMetric = new LinkedHashMap<>();
        for (ThresholdRule rule : allRules) {
            if (rule.getDeviceEui().equals(targetDevice.getDeviceEui())) {
                targetRules.put(rule.getMetric(), rule);
            } else {
                peerRulesByMetric.computeIfAbsent(rule.getMetric(), key -> new ArrayList<>()).add(rule);
            }
        }

        int synchronizedCount = 0;
        for (RequiredMetric required : REQUIRED_METRICS) {
            ThresholdRule current = targetRules.get(required.metric());
            RuleCondition condition = representativeCondition(
                    peerRulesByMetric.getOrDefault(required.metric(), List.of()),
                    current,
                    required
            );
            if (current == null) {
                createRule(
                        targetDevice.getDeviceEui(),
                        required.metric(),
                        condition.operator(),
                        condition.threshold(),
                        requesterId,
                        requestId
                );
                synchronizedCount++;
                continue;
            }

            boolean changed = current.changeCondition(
                    condition.operator(), condition.threshold(), requesterId);
            if (!changed) {
                continue;
            }
            updateRule(current);
            thresholdRuleHistoryRepository.save(ThresholdRuleHistory.record(
                    current, ChangeType.UPDATED, requesterId, requestId));
            eventPublisher.publishThresholdRuleChanged(current);
            synchronizedCount++;
        }

        if (synchronizedCount > 0) {
            log.info("센서 필수 룰 동기화: deviceEui={}, spaceId={}, 변경={}건",
                    targetDevice.getDeviceEui(), targetDevice.getSpaceId(), synchronizedCount);
        }
        return synchronizedCount;
    }

    /** 공간 하나의 metric 별 요약. 전체 기수의 룰을 미리 읽었으므로 여기서는 쿼리하지 않는다. */
    private SpaceThresholdResult summarize(
            Long spaceId,
            List<SensorDevice> devices,
            Map<String, List<ThresholdRule>> rulesByDeviceEui
    ) {
        Map<String, List<ThresholdRule>> rulesByMetric = new LinkedHashMap<>();
        for (SensorDevice device : devices) {
            for (ThresholdRule rule : rulesByDeviceEui.getOrDefault(
                    device.getDeviceEui(),
                    List.of()
            )) {
                rulesByMetric
                        .computeIfAbsent(rule.getMetric(), key -> new ArrayList<>())
                        .add(rule);
            }
        }

        List<MetricThresholdResult> metrics = new ArrayList<>();
        for (Map.Entry<String, List<ThresholdRule>> entry : rulesByMetric.entrySet()) {
            metrics.add(MetricThresholdResult.of(entry.getKey(), entry.getValue()));
        }

        return new SpaceThresholdResult(spaceId, devices.size(), metrics);
    }

    private Map<String, List<ThresholdRule>> indexRulesByDeviceEui(List<SensorDevice> devices) {
        Map<String, List<ThresholdRule>> rulesByDeviceEui = new HashMap<>();
        if (devices.isEmpty()) {
            return rulesByDeviceEui;
        }

        for (ThresholdRule rule : thresholdRuleRepository.findByDeviceEuiIn(deviceEuisOf(devices))) {
            rulesByDeviceEui
                    .computeIfAbsent(rule.getDeviceEui(), key -> new ArrayList<>())
                    .add(rule);
        }
        return rulesByDeviceEui;
    }

    private List<SensorDevice> findDevicesByCohortId(Long cohortId) {
        List<Long> spaceIds = spaceCohortQueryService.findSpaceIdsByCohortId(cohortId);
        return sensorDeviceRepository.findBySpaceIds(spaceIds);
    }

    private SensorDevice requireDeviceInCohort(
            String deviceEui,
            Long cohortId,
            SensorErrorCode errorCode
    ) {
        SensorDevice device = sensorDeviceRepository.findByDeviceEui(deviceEui)
                .orElseThrow(() -> new BusinessException(errorCode));

        boolean belongsToCohort = spaceCohortQueryService.findCohortId(device.getSpaceId())
                .filter(cohortId::equals)
                .isPresent();
        if (!belongsToCohort) {
            throw new BusinessException(errorCode);
        }
        return device;
    }

    private void requireSpaceInCohort(Long spaceId, Long cohortId) {
        boolean belongsToCohort = spaceCohortQueryService.findCohortId(spaceId)
                .filter(cohortId::equals)
                .isPresent();
        if (!belongsToCohort) {
            throw new BusinessException(SensorErrorCode.DEVICE_SPACE_NOT_FOUND);
        }
    }

    private ThresholdRule createRule(
            String deviceEui,
            String metric,
            Operator operator,
            Double threshold,
            UUID requesterId,
            String requestId
    ) {
        ThresholdRule thresholdRule;
        try {
            thresholdRule = ThresholdRule.create(deviceEui, metric, operator, threshold, requesterId);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(SensorErrorCode.RULE_INVALID_CONDITION, e.getMessage(), e);
        }

        if (thresholdRuleRepository.existsByDeviceEuiAndMetric(
                thresholdRule.getDeviceEui(), thresholdRule.getMetric())) {
            throw new BusinessException(SensorErrorCode.RULE_ALREADY_EXISTS);
        }

        // 유니크 위반은 경계 안에서 RULE_ALREADY_EXISTS로 변환된다.
        saveRule(thresholdRule);
        thresholdRuleHistoryRepository.save(ThresholdRuleHistory.record(
                thresholdRule, ChangeType.CREATED, requesterId, requestId));
        eventPublisher.publishThresholdRuleChanged(thresholdRule);
        return thresholdRule;
    }

    private ThresholdRule saveRule(ThresholdRule rule) {
        try {
            return thresholdRuleRepository.save(rule);
        } catch (SensorPersistenceException exception) {
            throw translatePersistenceException(exception);
        }
    }

    private void updateRule(ThresholdRule rule) {
        try {
            thresholdRuleRepository.update(rule);
        } catch (SensorPersistenceException exception) {
            throw translatePersistenceException(exception);
        }
    }

    private BusinessException translatePersistenceException(
            SensorPersistenceException exception
    ) {
        SensorErrorCode errorCode = switch (exception.getReason()) {
            case RULE_ALREADY_EXISTS -> SensorErrorCode.RULE_ALREADY_EXISTS;
            case RULE_VERSION_CONFLICT -> SensorErrorCode.RULE_VERSION_CONFLICT;
            default -> throw exception;
        };
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        return new BusinessException(errorCode, exception.getMessage(), cause);
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

    private List<MetricCondition> requireAllMetrics(List<MetricCondition> conditions) {
        Map<String, MetricCondition> byMetric = new LinkedHashMap<>();
        if (conditions != null) {
            for (MetricCondition condition : conditions) {
                String metric = condition == null ? null : condition.normalizedMetric();
                if (metric == null || REQUIRED_METRICS.stream().noneMatch(required -> required.metric().equals(metric))) {
                    throw new BusinessException(
                            SensorErrorCode.RULE_INVALID_CONDITION,
                            "필수 측정 항목은 co2, temperature, humidity입니다."
                    );
                }
                if (byMetric.put(metric, condition) != null) {
                    throw new BusinessException(
                            SensorErrorCode.RULE_INVALID_CONDITION,
                            "같은 측정 항목을 중복해서 저장할 수 없습니다: " + metric
                    );
                }
            }
        }

        if (byMetric.size() != REQUIRED_METRICS.size()) {
            throw new BusinessException(
                    SensorErrorCode.RULE_INVALID_CONDITION,
                    "co2, temperature, humidity 임계값을 모두 입력해야 합니다."
            );
        }
        return REQUIRED_METRICS.stream()
                .map(required -> byMetric.get(required.metric()))
                .toList();
    }

    private RuleCondition representativeCondition(
            List<ThresholdRule> peerRules,
            ThresholdRule current,
            RequiredMetric fallback
    ) {
        if (peerRules.isEmpty()) {
            if (current != null) {
                return new RuleCondition(current.getOperator(), current.getThreshold());
            }
            return new RuleCondition(fallback.operator(), fallback.threshold());
        }

        Map<RuleCondition, Integer> frequencies = new LinkedHashMap<>();
        for (ThresholdRule rule : peerRules) {
            RuleCondition condition = new RuleCondition(rule.getOperator(), rule.getThreshold());
            frequencies.merge(condition, 1, Integer::sum);
        }
        return frequencies.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElseThrow()
                .getKey();
    }

    private record RequiredMetric(String metric, Operator operator, Double threshold) {
    }

    private record RuleCondition(Operator operator, Double threshold) {
    }

}
