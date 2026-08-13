package site.omagotchi.learningservice.rule.infrastructure.persistence.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.rule.application.RuleErrorCode;
import site.omagotchi.learningservice.rule.domain.Operator;
import site.omagotchi.learningservice.rule.domain.ThresholdRule;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("임계치 룰 영속성 경계")
class ThresholdRuleJpaPersistenceTest {

    private static final String DEVICE_EUI = "0011223344556677";
    private static final String METRIC = "co2";
    private static final Long RULE_ID = 1L;
    private static final UUID REQUESTER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private ThresholdRuleJpaRepository thresholdRuleJpaRepository;

    private ThresholdRuleJpaPersistence thresholdRulePersistence;
    private ThresholdRule rule;

    @BeforeEach
    void setUp() {
        thresholdRulePersistence = new ThresholdRuleJpaPersistence(thresholdRuleJpaRepository);
        rule = ThresholdRule.create(DEVICE_EUI, METRIC, Operator.GT, 1_000.0, REQUESTER_ID);
    }

    @Test
    @DisplayName("저장은 flush까지 함께 한다 - 커밋까지 밀면 유니크 위반을 여기서 변환할 수 없다")
    void savesAndFlushes() {
        when(thresholdRuleJpaRepository.saveAndFlush(rule)).thenReturn(rule);

        thresholdRulePersistence.save(rule);

        verify(thresholdRuleJpaRepository).saveAndFlush(rule);
    }

    @Test
    @DisplayName("유니크 위반을 원인을 보존한 RULE_ALREADY_EXISTS 로 변환한다")
    void wrapsUniqueViolation() {
        DataIntegrityViolationException cause =
                new DataIntegrityViolationException("uq_threshold_rules_device_metric");
        when(thresholdRuleJpaRepository.saveAndFlush(rule)).thenThrow(cause);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> thresholdRulePersistence.save(rule));

        assertAll(
                () -> assertEquals(RuleErrorCode.RULE_ALREADY_EXISTS, exception.getErrorCode()),
                () -> assertInstanceOf(DataIntegrityViolationException.class, exception.getCause())
        );
    }

    @Test
    @DisplayName("낙관적 락 충돌을 원인을 보존한 RULE_VERSION_CONFLICT 로 변환한다")
    void wrapsLockFailure() {
        ObjectOptimisticLockingFailureException cause =
                new ObjectOptimisticLockingFailureException(ThresholdRule.class, RULE_ID);
        when(thresholdRuleJpaRepository.saveAndFlush(rule)).thenThrow(cause);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> thresholdRulePersistence.update(rule));

        assertAll(
                () -> assertEquals(RuleErrorCode.RULE_VERSION_CONFLICT, exception.getErrorCode()),
                () -> assertInstanceOf(ObjectOptimisticLockingFailureException.class, exception.getCause())
        );
    }
}
