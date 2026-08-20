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
    @DisplayName("룰이 없으면 인자 오류로 거부한다")
    void rejectsNullRule() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ThresholdRuleHistory.record(null, ChangeType.CREATED, REQUESTER_ID, REQUEST_ID)
        );
    }

    @Test
    @DisplayName("변경 유형이 없으면 인자 오류로 거부한다")
    void rejectsNullChangeType() {
        // 룰은 흠잡을 데 없는 상태여야 changeType 때문에 걸렸다고 말할 수 있다
        assertThrows(
                IllegalArgumentException.class,
                () -> ThresholdRuleHistory.record(savedRule(), null, REQUESTER_ID, REQUEST_ID)
        );
    }

    @Test
    @DisplayName("저장 전이면 식별자 미확정 상태 오류로 거부한다")
    void rejectsUnsavedRule() {
        ThresholdRule thresholdRule = newRule();   // id·version 둘 다 없음

        assertThrows(
                IllegalStateException.class,
                () -> ThresholdRuleHistory.record(thresholdRule, ChangeType.CREATED, REQUESTER_ID, REQUEST_ID)
        );
    }

    @Test
    @DisplayName("flush 전이면 버전 미확정 상태 오류로 거부한다")
    void rejectsUnflushedRule() {
        ThresholdRule thresholdRule = newRule();
        ReflectionTestUtils.setField(thresholdRule, "id", RULE_ID);   // id 만, version 은 아직 없음

        assertThrows(
                IllegalStateException.class,
                () -> ThresholdRuleHistory.record(thresholdRule, ChangeType.CREATED, REQUESTER_ID, REQUEST_ID)
        );
    }

    private ThresholdRule newRule() {
        return ThresholdRule.create(DEVICE_EUI, METRIC, Operator.GT, THRESHOLD, REQUESTER_ID);
    }

    /** id·version 이 DB 에서 채워진 상태를 흉내낸다. */
    private ThresholdRule savedRule() {
        ThresholdRule thresholdRule = newRule();
        ReflectionTestUtils.setField(thresholdRule, "id", RULE_ID);
        ReflectionTestUtils.setField(thresholdRule, "version", RULE_VERSION);
        return thresholdRule;
    }
}