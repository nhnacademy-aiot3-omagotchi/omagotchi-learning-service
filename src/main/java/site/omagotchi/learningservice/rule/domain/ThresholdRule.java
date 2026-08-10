package site.omagotchi.learningservice.rule.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

/**
 * 디바이스별 metric 에 대한 임계값 룰의 정본.
 *
 * 임계치 위반 판정은 rule-service 가 담당하고,
 * core 는 룰의 생성·변경·검증과 이력 보관만 책임진다.
 */
@Entity
@Table(name = "threshold_rules", schema = "learning_service")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ThresholdRule {

    private static final int MAX_DEVICE_EUI_LENGTH = 32;
    private static final int MAX_METRIC_LENGTH = 32;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_eui", nullable = false, length = MAX_DEVICE_EUI_LENGTH)
    private String deviceEui;

    /**
     * 측정 항목.
     *
     * rule-service 의 SensorReading.measurement() 와 문자열 완전 일치로 매칭되며,
     * 해당 어휘는 rule-service 의 정규화 단계가 소유하므로 enum 으로 닫지 않는다.
     * 대소문자·공백이 섞이면 룰이 조용히 미적중하므로 소문자로 정규화해 저장한다.
     */
    @Column(name = "metric", nullable = false, length = MAX_METRIC_LENGTH)
    private String metric;

    @Enumerated(EnumType.STRING)
    @Column(name = "operator", nullable = false, length = 8)
    private Operator operator;

    @Column(name = "threshold", nullable = false)
    private Double threshold;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "updated_by_user_id")
    private UUID updatedByUserId;


    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;


    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * 낙관적 락 버전.
     *
     * Hibernate 가 관리하므로 애플리케이션이 직접 대입하지 않는다.
     * 신규 행은 0 으로 시드되고 UPDATE 가 flush 될 때 증가한다.
     *
     * 이 값은 rule-service 로 ruleVersion 으로 전달되어 캐시의 last-write-wins
     * 판단에 쓰이므로, 변경 발행 전에 flush 로 증가를 확정해야 한다.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @PrePersist
    void onPersist() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public static ThresholdRule create(
            String deviceEui,
            String metric,
            Operator operator,
            Double threshold,
            UUID createdByUserId
    ) {
        ThresholdRule rule = new ThresholdRule();
        rule.deviceEui = requireText(deviceEui, "장치 EUI", MAX_DEVICE_EUI_LENGTH);
        rule.metric = normalizeMetric(metric);
        rule.operator = requireOperator(operator);
        rule.threshold = requireThreshold(threshold);
        rule.createdByUserId = createdByUserId;
        rule.updatedByUserId = createdByUserId;
        return rule;
    }

    /**
     * 연산자와 임계값을 변경한다.
     *
     * 값이 실제로 바뀌지 않으면 아무것도 하지 않는다. 변경 없는 UPDATE 로
     * 버전만 올라가서 rule-service 에 무의미한 룰이 재발행되는 것을 막는다.
     *
     * @return 실제로 변경되었으면 true — 호출부는 이때만 이력 기록과 발행을 수행한다.
     */
    public boolean changeCondition(
            Operator newOperator,
            Double newThreshold,
            UUID updatedByUserId
    ) {
        Operator nextOperator = requireOperator(newOperator);
        Double nextThreshold = requireThreshold(newThreshold);

        if (this.operator == nextOperator
                && this.threshold.compareTo(nextThreshold) == 0) {
            return false;
        }

        this.operator = nextOperator;
        this.threshold = nextThreshold;
        this.updatedByUserId = updatedByUserId;
        return true;
    }

    private static String normalizeMetric(String metric) {
        return requireText(metric, "측정 항목", MAX_METRIC_LENGTH)
                .toLowerCase(Locale.ROOT);
    }

    private static Operator requireOperator(Operator operator) {
        if (operator == null) {
            throw new IllegalArgumentException("비교 연산자는 필수입니다.");
        }
        return operator;
    }

    private static Double requireThreshold(Double threshold) {
        if (threshold == null || !Double.isFinite(threshold)) {
            throw new IllegalArgumentException("임계값은 유한한 실수여야 합니다.");
        }
        return threshold;
    }

    private static String requireText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "은(는) 필수입니다.");
        }

        String normalized = value.trim();

        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName + "은(는) %d자를 넘을 수 없습니다.".formatted(maxLength)
            );
        }

        return normalized;
    }
}
