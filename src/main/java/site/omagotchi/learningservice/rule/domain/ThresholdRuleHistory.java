package site.omagotchi.learningservice.rule.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * 임계치 룰 변경 이력. Append-only 이며 UPDATE/DELETE 를 하지 않는다.
 *
 * 변경 "이후" 상태만 기록한다 — previous_* 는 직전 rule_version 행의 next_* 에서
 * 파생 가능한 중복이라 두지 않으며, 필요하면 LAG 윈도우 함수로 조회한다.
 */
@Entity
@Table(name = "threshold_rule_histories", schema = "learning_service")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ThresholdRuleHistory {

    private static final int MAX_REQUEST_ID_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false, length = 20)
    private ChangeType changeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "next_operator", nullable = false, length = 8)
    private Operator nextOperator;

    @Column(name = "next_threshold", nullable = false)
    private Double nextThreshold;

    @Column(name = "changed_by_user_id")
    private UUID changedByUserId;

    @Column(name = "request_id", length = MAX_REQUEST_ID_LENGTH)
    private String requestId;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    @Column(name = "rule_version", nullable = false)
    private Long ruleVersion;

    @PrePersist
    void onPersist() {
        this.changedAt = Instant.now();
    }

    public static ThresholdRuleHistory record(
            ThresholdRule rule,
            ChangeType changeType,
            UUID changedByUserId,
            String requestId
    ) {

        if (rule == null) {
            throw new IllegalArgumentException("이력 기록 대상 룰은 필수입니다.");
        }
        if (changeType == null) {
            throw new IllegalArgumentException("변경 유형은 필수입니다.");
        }

        if (rule.getId() == null) {
            throw new IllegalStateException(
                    "룰 식별자가 확정되지 않았습니다. 이력 기록 전에 저장이 필요합니다."
            );
        }
        if (rule.getVersion() == null) {
            throw new IllegalStateException(
                    "룰 버전이 확정되지 않았습니다. 이력 기록 전에 flush 가 필요합니다."
            );
        }

        ThresholdRuleHistory history = new ThresholdRuleHistory();
        history.ruleId = rule.getId();
        history.ruleVersion = rule.getVersion();
        history.changeType = changeType;
        history.nextOperator = rule.getOperator();
        history.nextThreshold = rule.getThreshold();
        history.changedByUserId = changedByUserId;
        history.requestId = truncate(requestId, MAX_REQUEST_ID_LENGTH);
        return history;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
