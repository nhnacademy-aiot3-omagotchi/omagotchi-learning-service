package site.omagotchi.learningservice.rule.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.rule.application.command.CreateThresholdRuleCommand;
import site.omagotchi.learningservice.rule.application.command.UpdateThresholdRuleCommand;
import site.omagotchi.learningservice.rule.application.result.UpdateThresholdRuleResult;
import site.omagotchi.learningservice.rule.domain.Operator;
import site.omagotchi.learningservice.rule.domain.RuleErrorCode;
import site.omagotchi.learningservice.rule.domain.ThresholdRule;
import site.omagotchi.learningservice.rule.domain.ThresholdRuleHistory;
import site.omagotchi.learningservice.rule.infrastructure.SensorDeviceRepository;
import site.omagotchi.learningservice.rule.infrastructure.ThresholdRuleHistoryRepository;
import site.omagotchi.learningservice.rule.infrastructure.ThresholdRuleRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("임계치 룰 서비스")
class ThresholdRuleServiceTest {

    private static final String DEVICE_EUI = "0011223344556677";
    private static final String METRIC = "co2";
    private static final Double THRESHOLD = 1_000.0;
    private static final Long RULE_ID = 1L;
    private static final UUID REQUESTER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_REQUESTER_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String REQUEST_ID = "req-0001";

    @Mock
    private SensorDeviceRepository sensorDeviceRepository;

    @Mock
    private ThresholdRuleRepository thresholdRuleRepository;

    @Mock
    private ThresholdRuleHistoryRepository thresholdRuleHistoryRepository;

    @InjectMocks
    private ThresholdRuleService thresholdRuleService;

    @Nested
    @DisplayName("생성")
    class Create {

        @Test
        @DisplayName("룰을 저장하고 flush 한 뒤 CREATED 이력을 남기고 식별자를 반환한다")
        void creates() {
            when(sensorDeviceRepository.existsById(DEVICE_EUI)).thenReturn(true);
            when(thresholdRuleRepository.existsByDeviceEuiAndMetric(DEVICE_EUI, METRIC))
                    .thenReturn(false);
            // 원래 DB가 채워주는 id·version 을 save 시점에 대신 채워준다.
            // 이게 없으면 뒤이은 ThresholdRuleHistory.record 가 예외를 던진다.
            when(thresholdRuleRepository.save(any(ThresholdRule.class))).thenAnswer(invocation -> {
                ThresholdRule saved = invocation.getArgument(0);
                ReflectionTestUtils.setField(saved, "id", RULE_ID);
                ReflectionTestUtils.setField(saved, "version", 0L);
                return saved;
            });

            Long ruleId = thresholdRuleService.create(new CreateThresholdRuleCommand(
                    DEVICE_EUI, METRIC, Operator.GT, THRESHOLD, REQUESTER_ID, REQUEST_ID));

            assertAll(
                    () -> assertEquals(RULE_ID, ruleId),
                    () -> verify(thresholdRuleRepository).save(any(ThresholdRule.class)),
                    () -> verify(thresholdRuleRepository).flush(),
                    () -> verify(thresholdRuleHistoryRepository).save(any(ThresholdRuleHistory.class))
            );
        }

        @Test
        @DisplayName("등록되지 않은 장치면 저장 없이 DEVICE_NOT_FOUND 로 거부한다")
        void rejectsUnknownDevice() {
            when(sensorDeviceRepository.existsById(DEVICE_EUI)).thenReturn(false);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> thresholdRuleService.create(new CreateThresholdRuleCommand(
                            DEVICE_EUI, METRIC, Operator.GT, THRESHOLD, REQUESTER_ID, REQUEST_ID)));

            assertAll(
                    () -> assertEquals(RuleErrorCode.DEVICE_NOT_FOUND, exception.getErrorCode()),
                    () -> verifyNoInteractions(thresholdRuleRepository),
                    () -> verifyNoInteractions(thresholdRuleHistoryRepository)
            );
        }

        @Test
        @DisplayName("도메인 검증 실패를 원인을 보존한 RULE_INVALID_CONDITION 으로 변환한다")
        void wrapsDomainError() {
            when(sensorDeviceRepository.existsById(DEVICE_EUI)).thenReturn(true);

            // 측정 항목 33자 — 도메인이 IllegalArgumentException 을 던진다
            BusinessException exception = assertThrows(BusinessException.class,
                    () -> thresholdRuleService.create(new CreateThresholdRuleCommand(
                            DEVICE_EUI, "a".repeat(33), Operator.GT, THRESHOLD, REQUESTER_ID, REQUEST_ID)));

            assertAll(
                    () -> assertEquals(RuleErrorCode.RULE_INVALID_CONDITION, exception.getErrorCode()),
                    () -> assertInstanceOf(IllegalArgumentException.class, exception.getCause()),
                    () -> verifyNoInteractions(thresholdRuleRepository),
                    () -> verifyNoInteractions(thresholdRuleHistoryRepository)
            );
        }

        @Test
        @DisplayName("정규화된 값으로 중복을 검사하고 이미 있으면 저장 없이 거부한다")
        void rejectsDuplicate() {
            when(sensorDeviceRepository.existsById(DEVICE_EUI)).thenReturn(true);
            // DB 유니크(uq_threshold_rules_device_metric)가 보는 값과 같은 소문자로 조회해야 한다
            when(thresholdRuleRepository.existsByDeviceEuiAndMetric(DEVICE_EUI, METRIC))
                    .thenReturn(true);

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> thresholdRuleService.create(new CreateThresholdRuleCommand(
                            DEVICE_EUI, METRIC, Operator.GT, THRESHOLD, REQUESTER_ID, REQUEST_ID)));

            assertAll(
                    () -> assertEquals(RuleErrorCode.RULE_ALREADY_EXISTS, exception.getErrorCode()),
                    () -> verify(thresholdRuleRepository).existsByDeviceEuiAndMetric(DEVICE_EUI, METRIC),
                    () -> verify(thresholdRuleRepository, never()).save(any()),
                    () -> verifyNoInteractions(thresholdRuleHistoryRepository)
            );
        }

        @Test
        @DisplayName("경합으로 유니크 제약이 깨지면 RULE_ALREADY_EXISTS 로 변환한다")
        void wrapsUniqueViolation() {
            when(sensorDeviceRepository.existsById(DEVICE_EUI)).thenReturn(true);
            when(thresholdRuleRepository.existsByDeviceEuiAndMetric(DEVICE_EUI, METRIC))
                    .thenReturn(false);
            doThrow(new DataIntegrityViolationException("uq_threshold_rules_device_metric"))
                    .when(thresholdRuleRepository).flush();

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> thresholdRuleService.create(new CreateThresholdRuleCommand(
                            DEVICE_EUI, METRIC, Operator.GT, THRESHOLD, REQUESTER_ID, REQUEST_ID)));

            assertAll(
                    () -> assertEquals(RuleErrorCode.RULE_ALREADY_EXISTS, exception.getErrorCode()),
                    () -> assertInstanceOf(DataIntegrityViolationException.class, exception.getCause()),
                    () -> verifyNoInteractions(thresholdRuleHistoryRepository)
            );
        }
    }

    @Nested
    @DisplayName("수정")
    class Update {

        @Test
        @DisplayName("조건이 바뀌면 flush 후 UPDATED 이력을 남기고 갱신 결과를 반환한다")
        void updates() {
            ThresholdRule rule = ThresholdRule.create(
                    DEVICE_EUI, METRIC, Operator.GT, THRESHOLD, REQUESTER_ID);
            ReflectionTestUtils.setField(rule, "id", RULE_ID);
            ReflectionTestUtils.setField(rule, "version", 0L);
            when(thresholdRuleRepository.findById(RULE_ID)).thenReturn(Optional.of(rule));

            UpdateThresholdRuleResult result = thresholdRuleService.update(
                    new UpdateThresholdRuleCommand(
                            RULE_ID, 0L, Operator.GTE, 900.0, OTHER_REQUESTER_ID, REQUEST_ID));

            assertAll(
                    () -> assertTrue(result.changed()),
                    () -> assertEquals(Operator.GTE, rule.getOperator()),
                    () -> assertEquals(900.0, rule.getThreshold()),
                    () -> assertEquals(OTHER_REQUESTER_ID, rule.getUpdatedByUserId()),
                    () -> verify(thresholdRuleRepository).flush(),
                    () -> verify(thresholdRuleHistoryRepository).save(any(ThresholdRuleHistory.class))
            );
        }

        @Test
        @DisplayName("조건이 같으면 flush 도 이력 기록도 없이 현재 버전을 반환한다")
        void skipsUnchanged() {
            ThresholdRule rule = ThresholdRule.create(
                    DEVICE_EUI, METRIC, Operator.GT, THRESHOLD, REQUESTER_ID);
            ReflectionTestUtils.setField(rule, "id", RULE_ID);
            ReflectionTestUtils.setField(rule, "version", 3L);
            when(thresholdRuleRepository.findById(RULE_ID)).thenReturn(Optional.of(rule));

            UpdateThresholdRuleResult result = thresholdRuleService.update(
                    new UpdateThresholdRuleCommand(
                            RULE_ID, 3L, Operator.GT, THRESHOLD, OTHER_REQUESTER_ID, REQUEST_ID));

            assertAll(
                    () -> assertFalse(result.changed()),
                    () -> assertEquals(3L, result.ruleVersion()),
                    () -> assertEquals(REQUESTER_ID, rule.getUpdatedByUserId()),
                    () -> verify(thresholdRuleRepository, never()).flush(),
                    () -> verifyNoInteractions(thresholdRuleHistoryRepository)
            );
        }

        @Test
        @DisplayName("존재하지 않는 룰이면 RULE_NOT_FOUND 로 거부한다")
        void rejectsUnknownRule() {
            when(thresholdRuleRepository.findById(RULE_ID)).thenReturn(Optional.empty());

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> thresholdRuleService.update(new UpdateThresholdRuleCommand(
                            RULE_ID, 0L, Operator.GTE, 900.0, REQUESTER_ID, REQUEST_ID)));

            assertAll(
                    () -> assertEquals(RuleErrorCode.RULE_NOT_FOUND, exception.getErrorCode()),
                    () -> verifyNoInteractions(thresholdRuleHistoryRepository)
            );
        }

        @Test
        @DisplayName("기대 버전이 어긋나면 조건을 건드리지 않고 RULE_VERSION_CONFLICT 로 거부한다")
        void rejectsStaleVersion() {
            ThresholdRule rule = ThresholdRule.create(
                    DEVICE_EUI, METRIC, Operator.GT, THRESHOLD, REQUESTER_ID);
            ReflectionTestUtils.setField(rule, "id", RULE_ID);
            ReflectionTestUtils.setField(rule, "version", 2L);
            when(thresholdRuleRepository.findById(RULE_ID)).thenReturn(Optional.of(rule));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> thresholdRuleService.update(new UpdateThresholdRuleCommand(
                            RULE_ID, 0L, Operator.LT, 500.0, OTHER_REQUESTER_ID, REQUEST_ID)));

            assertAll(
                    () -> assertEquals(RuleErrorCode.RULE_VERSION_CONFLICT, exception.getErrorCode()),
                    // 선점 체크가 조건 변경보다 앞서므로 엔티티는 그대로다
                    () -> assertEquals(Operator.GT, rule.getOperator()),
                    () -> assertEquals(THRESHOLD, rule.getThreshold()),
                    () -> verify(thresholdRuleRepository, never()).flush(),
                    () -> verifyNoInteractions(thresholdRuleHistoryRepository)
            );
        }

        @Test
        @DisplayName("기대 버전이 없으면 충돌로 본다")
        void rejectsNullBaseVersion() {
            ThresholdRule rule = ThresholdRule.create(
                    DEVICE_EUI, METRIC, Operator.GT, THRESHOLD, REQUESTER_ID);
            ReflectionTestUtils.setField(rule, "id", RULE_ID);
            ReflectionTestUtils.setField(rule, "version", 0L);
            when(thresholdRuleRepository.findById(RULE_ID)).thenReturn(Optional.of(rule));

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> thresholdRuleService.update(new UpdateThresholdRuleCommand(
                            RULE_ID, null, Operator.GTE, 900.0, REQUESTER_ID, REQUEST_ID)));

            assertEquals(RuleErrorCode.RULE_VERSION_CONFLICT, exception.getErrorCode());
        }

        @Test
        @DisplayName("낙관적 락 충돌을 RULE_VERSION_CONFLICT 로 변환한다")
        void wrapsLockFailure() {
            ThresholdRule rule = ThresholdRule.create(
                    DEVICE_EUI, METRIC, Operator.GT, THRESHOLD, REQUESTER_ID);
            ReflectionTestUtils.setField(rule, "id", RULE_ID);
            ReflectionTestUtils.setField(rule, "version", 0L);
            when(thresholdRuleRepository.findById(RULE_ID)).thenReturn(Optional.of(rule));
            doThrow(new ObjectOptimisticLockingFailureException(ThresholdRule.class, RULE_ID))
                    .when(thresholdRuleRepository).flush();

            BusinessException exception = assertThrows(BusinessException.class,
                    () -> thresholdRuleService.update(new UpdateThresholdRuleCommand(
                            RULE_ID, 0L, Operator.GTE, 900.0, REQUESTER_ID, REQUEST_ID)));

            assertAll(
                    () -> assertEquals(RuleErrorCode.RULE_VERSION_CONFLICT, exception.getErrorCode()),
                    () -> assertInstanceOf(
                            ObjectOptimisticLockingFailureException.class, exception.getCause()),
                    () -> verifyNoInteractions(thresholdRuleHistoryRepository)
            );
        }
    }

    @Test
    @DisplayName("조회 - 저장된 룰을 그대로 반환하고 없으면 빈 목록을 반환한다")
    void readsAll() {
        ThresholdRule rule = ThresholdRule.create(
                DEVICE_EUI, METRIC, Operator.GT, THRESHOLD, REQUESTER_ID);
        when(thresholdRuleRepository.findAll()).thenReturn(List.of(rule), List.of());

        assertAll(
                () -> assertEquals(List.of(rule), thresholdRuleService.readAll()),
                () -> assertTrue(thresholdRuleService.readAll().isEmpty())
        );
    }
}
