package site.omagotchi.learningservice.rule.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("임계치 룰")
class ThresholdRuleTest {

    private static final String DEVICE_EUI = "0011223344556677";
    private static final String METRIC = "co2";
    private static final Double THRESHOLD = 1_000.0;
    private static final UUID REQUESTER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_REQUESTER_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Nested
    @DisplayName("생성")
    class Create {

        @Test
        @DisplayName("요청한 조건과 생성자를 반영하고 식별자·버전은 영속화에 맡긴다")
        void createTest() {
            ThresholdRule thresholdRule = ThresholdRule.create(
                    DEVICE_EUI,
                    METRIC,
                    Operator.GT,
                    THRESHOLD,
                    REQUESTER_ID
            );


            assertAll("요청한 값이 그대로 담기는가 테스트",
                    () -> assertEquals(DEVICE_EUI, thresholdRule.getDeviceEui()),
                    () -> assertEquals(METRIC, thresholdRule.getMetric()),
                    () -> assertEquals(Operator.GT, thresholdRule.getOperator()),
                    () -> assertEquals(THRESHOLD, thresholdRule.getThreshold())
            );


            assertAll("생성한 유저와 업데이트 유저의 UUID가 같은지 테스트",
                    () -> assertEquals(REQUESTER_ID, thresholdRule.getCreatedByUserId()),
                    () -> assertEquals(REQUESTER_ID, thresholdRule.getUpdatedByUserId())
            );

            assertAll("룰 아이디와 버전은 영속화 전에 비어있음",
                    () -> assertNull(thresholdRule.getId()),
                    () -> assertNull(thresholdRule.getVersion())
            );
        }

        @Test
        @DisplayName("측정 항목은 공백 제거 후 소문자로, 장치 EUI 는 공백만 제거해 저장한다")
        void normalizes() {
            String testMetric = "CO2";
            String testEUI = " TEST ";
            ThresholdRule thresholdRule = ThresholdRule.create(
                    testEUI,
                    testMetric,
                    Operator.GT,
                    THRESHOLD,
                    REQUESTER_ID
            );
            assertAll(
                    () -> assertEquals("TEST", thresholdRule.getDeviceEui()),
                    () -> assertEquals("co2", thresholdRule.getMetric())
            );
        }

        @Test
        @DisplayName("필수 값이 없거나 공백뿐이면 거부한다")
        void rejectsBlank() {
            assertAll("null일경우",
                    () -> assertThrows(IllegalArgumentException.class, () -> ThresholdRule.create(null, METRIC, Operator.GT, THRESHOLD, REQUESTER_ID)),
                    () -> assertThrows(IllegalArgumentException.class, () -> ThresholdRule.create(DEVICE_EUI, null, Operator.GT, THRESHOLD, REQUESTER_ID)),
                    () -> assertThrows(IllegalArgumentException.class, () -> ThresholdRule.create(DEVICE_EUI, METRIC, null, THRESHOLD, REQUESTER_ID)),
                    () -> assertThrows(IllegalArgumentException.class, () -> ThresholdRule.create(DEVICE_EUI, METRIC, Operator.GT, null, REQUESTER_ID))
            );

            assertAll("공백일경우",
                    () -> assertThrows(IllegalArgumentException.class, () -> ThresholdRule.create("", METRIC, Operator.GT, THRESHOLD, REQUESTER_ID)),
                    () -> assertThrows(IllegalArgumentException.class, () -> ThresholdRule.create(DEVICE_EUI, "", Operator.GT, THRESHOLD, REQUESTER_ID))
            );

        }

        @Test
        @DisplayName("길이 제한은 공백 제거 후 기준이며 32자까지 허용한다")
        void limitsLength() {
            String testEUI = "test test test test test test test test test";//총 44자
            String testMetric = "test test test test test test test test test";

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> ThresholdRule.create(testEUI, METRIC, Operator.GT, THRESHOLD, REQUESTER_ID)),
                    () -> assertThrows(IllegalArgumentException.class, () -> ThresholdRule.create(DEVICE_EUI, testMetric, Operator.GT, THRESHOLD, REQUESTER_ID))
            );

        }

        @Test
        @DisplayName("임계값은 유한한 실수만 허용한다")
        void requiresFinite() {
            Double infiniteThreshold = Double.POSITIVE_INFINITY;

            assertThrows(IllegalArgumentException.class, () -> ThresholdRule.create(DEVICE_EUI, METRIC, Operator.GT, infiniteThreshold, REQUESTER_ID));
        }
    }

    @Nested
    @DisplayName("업데이트")
    class ChangeCondition {

        @Test
        @DisplayName("연산자나 임계값이 바뀌면 새 값과 변경자를 반영하고 true 를 반환한다")
        void changes() {
            ThresholdRule thresholdRule = ThresholdRule.create(
                    DEVICE_EUI,
                    METRIC,
                    Operator.GT,
                    THRESHOLD,
                    REQUESTER_ID
            );

            Operator updatedOperator = Operator.GTE;
            Double updatedThreshold = 900.0;


            boolean result = thresholdRule.changeCondition(updatedOperator, updatedThreshold, OTHER_REQUESTER_ID);

            assertAll(
                    () -> assertTrue(result),
                    () -> assertEquals(Operator.GTE, thresholdRule.getOperator()),
                    () -> assertEquals(updatedThreshold, thresholdRule.getThreshold()),
                    () -> assertEquals(OTHER_REQUESTER_ID, thresholdRule.getUpdatedByUserId())
            );
        }

        @Test
        @DisplayName("조건이 완전히 같으면 변경자도 그대로 두고 false 를 반환한다")
        void keepsSame() {
            ThresholdRule thresholdRule = ThresholdRule.create(
                    DEVICE_EUI,
                    METRIC,
                    Operator.GT,
                    THRESHOLD,
                    REQUESTER_ID
            );

            Operator updatedOperator = Operator.GT;
            Double updatedThreshold = 1_000.0;

            boolean result = thresholdRule.changeCondition(updatedOperator, updatedThreshold, OTHER_REQUESTER_ID);

            assertFalse(result);
        }

    }
}