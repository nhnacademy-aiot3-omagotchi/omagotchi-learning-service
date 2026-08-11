package site.omagotchi.learningservice.rule.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("임계치 룰 이력")
class ThresholdRuleHistoryTest {
    private static final Long RULE_ID = 1L;
    private static final Long RULE_VERSION = 0L;
    private static final String DEVICE_EUI = "0011223344556677";
    private static final String METRIC = "co2";
    private static final Double THRESHOLD = 1_000.0;
    private static final UUID REQUESTER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String REQUEST_ID = "req-0001";

    @Test
    @DisplayName("변경 이후 상태와 룰 버전, 변경자를 스냅샷으로 남긴다")
    void snapshots() {
        ThresholdRule thresholdRule = ThresholdRule.create(
                DEVICE_EUI,
                METRIC,
                Operator.GT,
                THRESHOLD,
                REQUESTER_ID
        );

        ReflectionTestUtils.setField(thresholdRule, "id", RULE_ID);
        ReflectionTestUtils.setField(thresholdRule, "version", RULE_VERSION);

        ThresholdRuleHistory history = ThresholdRuleHistory.record(
                thresholdRule,
                ChangeType.CREATED,
                REQUESTER_ID,
                REQUEST_ID
        );

        assertAll(
                () -> assertEquals(RULE_ID, history.getRuleId()),
                () -> assertEquals(RULE_VERSION, history.getRuleVersion()),
                () -> assertEquals(ChangeType.CREATED, history.getChangeType()),
                () -> assertEquals(Operator.GT, history.getNextOperator()),
                () -> assertEquals(THRESHOLD, history.getNextThreshold()),
                () -> assertEquals(REQUESTER_ID, history.getChangedByUserId()),
                () -> assertEquals(REQUEST_ID, history.getRequestId())
        );
    }

    @Test
    @DisplayName("요청 식별자는 없어도 되고 100자를 넘으면 잘라 담는다")
    void handlesRequestId() {
        ThresholdRule thresholdRule = ThresholdRule.create(
                DEVICE_EUI,
                METRIC,
                Operator.GT,
                THRESHOLD,
                REQUESTER_ID
        );
        String exactly100 = "a".repeat(100);
        String tooLong = exactly100 + "b";   // 101자

        ReflectionTestUtils.setField(thresholdRule, "id", 1L);
        ReflectionTestUtils.setField(thresholdRule, "version", 1L);

        assertAll(
                () -> assertNull(ThresholdRuleHistory.record(thresholdRule, ChangeType.CREATED, REQUESTER_ID, null).getRequestId()),
                () -> assertEquals(exactly100, ThresholdRuleHistory.record(thresholdRule, ChangeType.CREATED, REQUESTER_ID, exactly100).getRequestId()),
                () -> assertEquals(exactly100, ThresholdRuleHistory.record(thresholdRule, ChangeType.CREATED, REQUESTER_ID, tooLong).getRequestId())
        );
    }

    @Test
    @DisplayName("룰이 식별되지 않거나 변경 유형이 없으면 거부한다")
    void rejectsUnidentified() {
        ThresholdRule thresholdRule = ThresholdRule.create(
                DEVICE_EUI,
                METRIC,
                Operator.GT,
                THRESHOLD,
                REQUESTER_ID
        );

        assertThrows(IllegalArgumentException.class,  () -> ThresholdRuleHistory.record(thresholdRule, ChangeType.CREATED, REQUESTER_ID, REQUEST_ID));

        ReflectionTestUtils.setField(thresholdRule, "id", RULE_ID);
        ReflectionTestUtils.setField(thresholdRule, "version", RULE_VERSION);

        assertThrows(IllegalArgumentException.class, () -> ThresholdRuleHistory.record(thresholdRule, null, REQUESTER_ID, REQUEST_ID));
    }

    @Test
    @DisplayName("버전이 확정되지 않았으면 flush 누락으로 보고 거부한다")
    void rejectsUnflushed() {
        ThresholdRule thresholdRule = ThresholdRule.create(
                DEVICE_EUI,
                METRIC,
                Operator.GT,
                THRESHOLD,
                REQUESTER_ID
        );

        ReflectionTestUtils.setField(thresholdRule, "id", RULE_ID);
        assertThrows(IllegalStateException.class, () -> ThresholdRuleHistory.record(thresholdRule, ChangeType.CREATED, REQUESTER_ID, REQUEST_ID));

    }
}