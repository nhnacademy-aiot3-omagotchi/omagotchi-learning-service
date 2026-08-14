package site.omagotchi.learningservice.rule.infrastructure.persistence.repository;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import site.omagotchi.learningservice.TestcontainersConfiguration;
import site.omagotchi.learningservice.global.config.JpaAuditingConfig;
import site.omagotchi.learningservice.global.config.QueryDslConfig;
import site.omagotchi.learningservice.rule.domain.ChangeType;
import site.omagotchi.learningservice.rule.domain.Operator;
import site.omagotchi.learningservice.rule.domain.ThresholdRule;
import site.omagotchi.learningservice.rule.domain.ThresholdRuleHistory;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 실제 PostgreSQL 로만 검증할 수 있는 것만 여기 둔다.
 *
 * <p>단위 테스트는 리포지토리를 모킹하므로 DB 가 채우는 값(@Version 시드·증가)을
 * {@code ReflectionTestUtils} 로 대신 넣는다. 그 전제 자체가 참인지는 여기서만 확인된다.</p>
 *
 * <p>제약 <b>이름</b>까지 단언하는 이유는, 예외를 기능 오류 코드로 옮기는 쪽이
 * 이 이름에 의존하기 때문이다. 마이그레이션에서 이름을 바꾸면 여기가 먼저 깨져야 한다.</p>
 *
 * <p><b>메서드 하나당 위반 하나.</b> 제약 위반이 나면 영속성 컨텍스트가 오염되고
 * 트랜잭션이 rollback-only 가 되어 뒤이은 연산이 엉뚱한 예외로 실패한다.</p>
 */
// @DataJpaTest 는 다른 도메인의 Spring Data 커스텀 구현까지 스캔하므로
// 그쪽이 요구하는 JPAQueryFactory·Auditing 인프라도 같이 올린다
@Import({
        TestcontainersConfiguration.class,
        QueryDslConfig.class,
        JpaAuditingConfig.class
})
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("임계치 룰 DB 제약")
class ThresholdRuleConstraintIT {

    /** V22 시드에 실재하는 장비. FK 를 통과해야 하는 테스트는 이 값을 쓴다. */
    private static final String SEEDED_DEVICE_EUI = "24e124126d152862";
    private static final String UNKNOWN_DEVICE_EUI = "0000000000000000";
    private static final String METRIC = "co2";
    private static final Double THRESHOLD = 1_000.0;
    private static final UUID REQUESTER_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private ThresholdRuleJpaRepository thresholdRuleJpaRepository;

    @Autowired
    private ThresholdRuleHistoryJpaRepository thresholdRuleHistoryJpaRepository;

    @Nested
    @DisplayName("낙관적 락 버전")
    class Version {

        @Test
        @DisplayName("신규 행의 version 은 0 으로 시드된다")
        void seedsToZero() {
            ThresholdRule saved = thresholdRuleJpaRepository.saveAndFlush(rule(METRIC));

            // 단위 테스트가 ReflectionTestUtils 로 손수 넣던 값
            assertEquals(0L, saved.getVersion());
        }

        @Test
        @DisplayName("UPDATE 가 flush 되면 version 이 증가한다")
        void incrementsOnUpdate() {
            ThresholdRule saved = thresholdRuleJpaRepository.saveAndFlush(rule(METRIC));

            saved.changeCondition(Operator.LT, 500.0, REQUESTER_ID);
            thresholdRuleJpaRepository.saveAndFlush(saved);

            // 이력의 rule_version 이 이 값을 그대로 쓰므로 증가 시점이 곧 계약이다
            assertEquals(1L, saved.getVersion());
        }
    }

    @Nested
    @DisplayName("threshold_rules 제약")
    class RuleTable {

        @Test
        @DisplayName("같은 장치·측정항목이면 uq_threshold_rules_device_metric 위반")
        void rejectsDuplicateDeviceMetric() {
            thresholdRuleJpaRepository.saveAndFlush(rule(METRIC));

            DataIntegrityViolationException exception = assertThrows(
                    DataIntegrityViolationException.class,
                    () -> thresholdRuleJpaRepository.saveAndFlush(rule(METRIC)));

            assertEquals("uq_threshold_rules_device_metric", constraintName(exception));
        }

        @Test
        @DisplayName("등록되지 않은 장치면 fk_threshold_rules_device 위반")
        void rejectsUnknownDevice() {
            ThresholdRule orphan = ThresholdRule.create(
                    UNKNOWN_DEVICE_EUI, METRIC, Operator.GT, THRESHOLD, REQUESTER_ID);

            DataIntegrityViolationException exception = assertThrows(
                    DataIntegrityViolationException.class,
                    () -> thresholdRuleJpaRepository.saveAndFlush(orphan));

            // 유니크가 아니라 FK 다. 둘을 같은 오류 코드로 뭉치면 404 가 409 로 나간다
            assertEquals("fk_threshold_rules_device", constraintName(exception));
        }

        @Test
        @DisplayName("도메인 정규화 결과가 ck_threshold_rules_metric 을 통과한다")
        void acceptsNormalizedMetric() {
            // 도메인은 trim + toLowerCase(ROOT), DB CHECK 는 metric = LOWER(BTRIM(metric)).
            // 둘이 어긋나면 정상 입력이 500 이 된다
            ThresholdRule saved = thresholdRuleJpaRepository.saveAndFlush(
                    ThresholdRule.create(
                            SEEDED_DEVICE_EUI, "  CO2  ", Operator.GT, THRESHOLD, REQUESTER_ID));

            assertEquals(METRIC, saved.getMetric());
        }
    }

    @Nested
    @DisplayName("threshold_rule_histories 제약")
    class HistoryTable {

        @Test
        @DisplayName("룰 버전당 이력은 하나뿐이다")
        void rejectsDuplicateVersion() {
            ThresholdRule saved = thresholdRuleJpaRepository.saveAndFlush(rule(METRIC));
            thresholdRuleHistoryJpaRepository.saveAndFlush(
                    ThresholdRuleHistory.record(saved, ChangeType.CREATED, REQUESTER_ID, "req-1"));

            // 같은 version 으로 한 번 더 — flush 를 빠뜨린 코드가 만드는 상황이다
            DataIntegrityViolationException exception = assertThrows(
                    DataIntegrityViolationException.class,
                    () -> thresholdRuleHistoryJpaRepository.saveAndFlush(
                            ThresholdRuleHistory.record(
                                    saved, ChangeType.UPDATED, REQUESTER_ID, "req-2")));

            assertEquals("uq_threshold_rule_history_version", constraintName(exception));
        }

        @Test
        @DisplayName("룰 갱신과 이력 기록이 같은 트랜잭션에서 버전을 맞춘다")
        void recordsAfterFlush() {
            ThresholdRule saved = thresholdRuleJpaRepository.saveAndFlush(rule(METRIC));
            thresholdRuleHistoryJpaRepository.saveAndFlush(
                    ThresholdRuleHistory.record(saved, ChangeType.CREATED, REQUESTER_ID, "req-1"));

            saved.changeCondition(Operator.LT, 500.0, REQUESTER_ID);
            thresholdRuleJpaRepository.saveAndFlush(saved);
            ThresholdRuleHistory updated = thresholdRuleHistoryJpaRepository.saveAndFlush(
                    ThresholdRuleHistory.record(saved, ChangeType.UPDATED, REQUESTER_ID, "req-2"));

            assertAll(
                    () -> assertEquals(1L, updated.getRuleVersion()),
                    () -> assertEquals(2, thresholdRuleHistoryJpaRepository.count())
            );
        }
    }

    private ThresholdRule rule(String metric) {
        return ThresholdRule.create(
                SEEDED_DEVICE_EUI, metric, Operator.GT, THRESHOLD, REQUESTER_ID);
    }

    /** Spring 이 감싼 예외에서 Hibernate 가 뽑아 둔 제약명을 꺼낸다. */
    private String constraintName(DataIntegrityViolationException exception) {
        ConstraintViolationException violation =
                assertInstanceOf(ConstraintViolationException.class, exception.getCause());
        return violation.getConstraintName();
    }
}
