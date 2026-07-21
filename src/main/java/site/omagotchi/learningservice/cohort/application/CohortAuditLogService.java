package site.omagotchi.learningservice.cohort.application;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.dto.result.CohortAuditLogResponse;
import site.omagotchi.learningservice.cohort.domain.CohortAuditLog;
import site.omagotchi.learningservice.cohort.infrastructure.CohortAuditLogRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CohortAuditLogService {

    private final CohortAuditLogRepository auditLogRepository;

    /**
     * cohort 도메인에서 발생한 주요 변경 이벤트를 감사 로그로 저장한다.
     * 승인/거절/역할 변경처럼 추적이 필요한 작업에서 호출한다.
     */
    @Transactional
    public CohortAuditLogResponse createAuditLog(
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
        CohortAuditLog auditLog = CohortAuditLog.create(
                cohortId,
                actorUserId,
                targetType,
                targetId,
                action,
                beforeValue,
                afterValue,
                reason,
                requestId
        );
        return CohortAuditLogResponse.from(auditLogRepository.save(auditLog));
    }

    /**
     * 특정 기수에서 발생한 감사 로그를 최신순으로 조회한다.
     * 관리자 화면의 변경 이력 조회에 사용한다.
     */
    public List<CohortAuditLogResponse> getAuditLogs(Long cohortId) {
        return auditLogRepository.findByCohortIdOrderByOccurredAtDesc(cohortId).stream()
                .map(CohortAuditLogResponse::from)
                .toList();
    }
}
