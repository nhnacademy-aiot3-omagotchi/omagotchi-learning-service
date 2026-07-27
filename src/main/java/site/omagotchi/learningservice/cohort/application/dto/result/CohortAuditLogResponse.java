package site.omagotchi.learningservice.cohort.application.dto.result;

import com.fasterxml.jackson.databind.JsonNode;
import site.omagotchi.learningservice.cohort.domain.CohortAuditLog;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 기수 관리 감사 로그 조회 결과
 */
public record CohortAuditLogResponse(
        Long id,
        Long cohortId,
        UUID actorUserId,
        String targetType,
        Long targetId,
        String action,
        JsonNode beforeValue,
        JsonNode afterValue,
        String reason,
        String requestId,
        OffsetDateTime occurredAt
) {

    public static CohortAuditLogResponse from(CohortAuditLog auditLog) {
        return new CohortAuditLogResponse(
                auditLog.getId(),
                auditLog.getCohortId(),
                auditLog.getActorUserId(),
                auditLog.getTargetType(),
                auditLog.getTargetId(),
                auditLog.getAction(),
                auditLog.getBeforeValue(),
                auditLog.getAfterValue(),
                auditLog.getReason(),
                auditLog.getRequestId(),
                auditLog.getOccurredAt()
        );
    }
}
