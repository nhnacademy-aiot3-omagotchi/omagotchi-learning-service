package site.omagotchi.learningservice.cohort.domain;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * cohort 도메인에서 발생한 주요 변경 이력을 저장
 * 승인, 거절, 역할 변경처럼 추적이 필요한 작업의 변경 전후 값과 요청 식별자 남김
 */
@Getter
@Entity
@Table(name = "cohort_audit_logs", schema = "learning_service")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CohortAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cohort_id", nullable = false)
    private Long cohortId;

    @Column(name = "actor_user_id", nullable = false)
    private Long actorUserId;

    @Column(name = "target_type", nullable = false, length = 30)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(nullable = false, length = 50)
    private String action;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_value", columnDefinition = "jsonb")
    private JsonNode beforeValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_value", columnDefinition = "jsonb")
    private JsonNode afterValue;

    @Column(length = 500)
    private String reason;

    @Column(name = "request_id", length = 100)
    private String requestId;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    public static CohortAuditLog create(
            Long cohortId,
            Long actorUserId,
            String targetType,
            Long targetId,
            String action,
            JsonNode beforeValue,
            JsonNode afterValue,
            String reason,
            String requestId
    ) {
        CohortAuditLog auditLog = new CohortAuditLog();
        auditLog.cohortId = cohortId;
        auditLog.actorUserId = actorUserId;
        auditLog.targetType = requireText(targetType, "targetType");
        auditLog.targetId = targetId;
        auditLog.action = requireText(action, "action");
        auditLog.beforeValue = beforeValue;
        auditLog.afterValue = afterValue;
        auditLog.reason = reason;
        auditLog.requestId = requireText(requestId, "requestId");
        auditLog.occurredAt = OffsetDateTime.now();
        return auditLog;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "은 필수입니다.");
        }
        return value;
    }
}
