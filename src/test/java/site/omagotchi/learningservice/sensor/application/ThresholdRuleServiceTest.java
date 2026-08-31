package site.omagotchi.learningservice.sensor.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.cohort.application.CohortErrorCode;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.sensor.application.command.ApplySpaceThresholdCommand;
import site.omagotchi.learningservice.sensor.application.command.CreateThresholdRuleCommand;
import site.omagotchi.learningservice.sensor.application.command.UpdateThresholdRuleCommand;
import site.omagotchi.learningservice.sensor.application.port.SensorDeviceRepository;
import site.omagotchi.learningservice.sensor.application.port.ThresholdRuleEventPublisher;
import site.omagotchi.learningservice.sensor.application.port.ThresholdRuleHistoryRepository;
import site.omagotchi.learningservice.sensor.application.port.ThresholdRuleRepository;
import site.omagotchi.learningservice.sensor.domain.Operator;
import site.omagotchi.learningservice.sensor.domain.SensorDevice;
import site.omagotchi.learningservice.sensor.domain.ThresholdRule;
import site.omagotchi.learningservice.space.application.SpaceCohortQueryService;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThresholdRuleServiceTest {

    private static final Long COHORT_ID = 1L;
    private static final Long SPACE_ID = 10L;
    private static final Long OTHER_SPACE_ID = 20L;
    private static final UUID REQUESTER_ID = UUID.fromString(
            "00000000-0000-0000-0000-000000000001"
    );
    private static final String DEVICE_EUI = "0011223344556677";
    private static final String OTHER_DEVICE_EUI = "8899aabbccddeeff";
    private static final Instant INSTALLED_AT = Instant.parse("2026-08-30T00:00:00Z");

    @Mock
    private SensorDeviceRepository sensorDeviceRepository;

    @Mock
    private ThresholdRuleRepository thresholdRuleRepository;

    @Mock
    private ThresholdRuleHistoryRepository thresholdRuleHistoryRepository;

    @Mock
    private ThresholdRuleEventPublisher eventPublisher;

    @Mock
    private CohortAccessService cohortAccessService;

    @Mock
    private SpaceCohortQueryService spaceCohortQueryService;

    @InjectMocks
    private ThresholdRuleService thresholdRuleService;

    @Test
    void rejectsRuleCreationForDeviceOwnedByAnotherCohort() {
        when(sensorDeviceRepository.findByDeviceEui(DEVICE_EUI))
                .thenReturn(Optional.of(device(DEVICE_EUI, OTHER_SPACE_ID)));
        when(spaceCohortQueryService.findCohortId(OTHER_SPACE_ID))
                .thenReturn(Optional.of(2L));

        BusinessException thrown = catchThrowableOfType(BusinessException.class, () ->
                thresholdRuleService.create(
                        COHORT_ID,
                        REQUESTER_ID,
                        "request-1",
                        new CreateThresholdRuleCommand(
                                DEVICE_EUI,
                                "co2",
                                Operator.GT,
                                1000.0
                        )
                )
        );

        assertThat(thrown.getErrorCode()).isEqualTo(SensorErrorCode.DEVICE_NOT_FOUND);
        verify(thresholdRuleRepository, never()).save(any());
        verifyNoInteractions(thresholdRuleHistoryRepository, eventPublisher);
    }

    @Test
    void rejectsWriteBeforeReadingResourcesWhenRequesterIsNotManager() {
        doThrow(new BusinessException(CohortErrorCode.COHORT_MANAGER_REQUIRED))
                .when(cohortAccessService)
                .requireManager(COHORT_ID, REQUESTER_ID);

        BusinessException thrown = catchThrowableOfType(BusinessException.class, () ->
                thresholdRuleService.update(
                        COHORT_ID,
                        REQUESTER_ID,
                        "request-1",
                        1L,
                        new UpdateThresholdRuleCommand(0L, Operator.GTE, 900.0)
                )
        );

        assertThat(thrown.getErrorCode())
                .isEqualTo(CohortErrorCode.COHORT_MANAGER_REQUIRED);
        verifyNoInteractions(
                sensorDeviceRepository,
                thresholdRuleRepository,
                thresholdRuleHistoryRepository,
                eventPublisher,
                spaceCohortQueryService
        );
    }

    @Test
    void readsOnlyRulesForDevicesInRequestedCohort() {
        SensorDevice device = device(DEVICE_EUI, SPACE_ID);
        ThresholdRule rule = rule(DEVICE_EUI, "co2", 1000.0);
        when(spaceCohortQueryService.findSpaceIdsByCohortId(COHORT_ID))
                .thenReturn(List.of(SPACE_ID));
        when(sensorDeviceRepository.findBySpaceIds(List.of(SPACE_ID)))
                .thenReturn(List.of(device));
        when(thresholdRuleRepository.findByDeviceEuiIn(List.of(DEVICE_EUI)))
                .thenReturn(List.of(rule));

        List<ThresholdRule> result = thresholdRuleService.findAllByCohort(
                COHORT_ID,
                REQUESTER_ID
        );

        // 임계치는 읽기도 매니저다 — 사용자 화면에 나가지 않는다
        verify(cohortAccessService).requireManager(COHORT_ID, REQUESTER_ID);
        assertThat(result).containsExactly(rule);
    }

    @Test
    void feedsRuleEngineOnlyWithRulesOfActiveDevices() {
        thresholdRuleService.readAllForRuleEngine();

        // 전수 조회로 되돌아가면 회수된 센서의 룰이 다시 나가 조치가 계속 일어난다
        verify(thresholdRuleRepository).findAllWithActiveDevice();
    }

    @Test
    void rejectsReadingRulesWhenRequesterIsNotManager() {
        doThrow(new BusinessException(CohortErrorCode.COHORT_MANAGER_REQUIRED))
                .when(cohortAccessService)
                .requireManager(COHORT_ID, REQUESTER_ID);

        assertThat(catchThrowableOfType(BusinessException.class, () ->
                thresholdRuleService.findAllByCohort(COHORT_ID, REQUESTER_ID)
        ).getErrorCode()).isEqualTo(CohortErrorCode.COHORT_MANAGER_REQUIRED);

        assertThat(catchThrowableOfType(BusinessException.class, () ->
                thresholdRuleService.findAllBySpace(COHORT_ID, REQUESTER_ID)
        ).getErrorCode()).isEqualTo(CohortErrorCode.COHORT_MANAGER_REQUIRED);

        verifyNoInteractions(
                sensorDeviceRepository,
                thresholdRuleRepository,
                spaceCohortQueryService
        );
    }

    @Test
    void readsRulesOnceWhenSummarizingMultipleSpaces() {
        SensorDevice first = device(DEVICE_EUI, SPACE_ID);
        SensorDevice second = device(OTHER_DEVICE_EUI, OTHER_SPACE_ID);
        List<String> deviceEuis = List.of(DEVICE_EUI, OTHER_DEVICE_EUI);
        when(spaceCohortQueryService.findSpaceIdsByCohortId(COHORT_ID))
                .thenReturn(List.of(SPACE_ID, OTHER_SPACE_ID));
        when(sensorDeviceRepository.findBySpaceIds(List.of(SPACE_ID, OTHER_SPACE_ID)))
                .thenReturn(List.of(first, second));
        when(thresholdRuleRepository.findByDeviceEuiIn(deviceEuis))
                .thenReturn(List.of(
                        rule(DEVICE_EUI, "co2", 1000.0),
                        rule(OTHER_DEVICE_EUI, "temperature", 26.0)
                ));

        var result = thresholdRuleService.findAllBySpace(COHORT_ID, REQUESTER_ID);

        assertThat(result).hasSize(2);
        verify(thresholdRuleRepository).findByDeviceEuiIn(deviceEuis);
    }

    @Test
    void rejectsBulkApplyWhenSpaceBelongsToAnotherCohort() {
        when(spaceCohortQueryService.findCohortId(OTHER_SPACE_ID))
                .thenReturn(Optional.of(2L));
        ApplySpaceThresholdCommand command = new ApplySpaceThresholdCommand(List.of(
                new ApplySpaceThresholdCommand.MetricCondition(
                        "co2",
                        Operator.GT,
                        1000.0
                )
        ));

        BusinessException thrown = catchThrowableOfType(BusinessException.class, () ->
                thresholdRuleService.applyToSpace(
                        COHORT_ID,
                        REQUESTER_ID,
                        "request-1",
                        OTHER_SPACE_ID,
                        command
                )
        );

        assertThat(thrown.getErrorCode())
                .isEqualTo(SensorErrorCode.DEVICE_SPACE_NOT_FOUND);
        verify(sensorDeviceRepository, never()).findBySpaceIds(any());
        verifyNoInteractions(thresholdRuleRepository, thresholdRuleHistoryRepository, eventPublisher);
    }

    private SensorDevice device(String deviceEui, Long spaceId) {
        return SensorDevice.create(
                deviceEui,
                spaceId,
                "AM103",
                "환경 센서",
                null,
                60,
                INSTALLED_AT
        );
    }

    private ThresholdRule rule(String deviceEui, String metric, Double threshold) {
        return ThresholdRule.create(
                deviceEui,
                metric,
                Operator.GT,
                threshold,
                REQUESTER_ID
        );
    }
}
