package site.omagotchi.learningservice.study.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "command_receipts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Immutable // dirty checking과 UPDATE 생성을 억제
public class CommandReceiptEntity {

    @EmbeddedId
    private CommandReceiptId id;

    @Enumerated(EnumType.STRING)
    @Column(name = "command_code", nullable = false, length = 40, updatable = false)
    private CommandCode commandCode;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(
            name = "request_hash",
            nullable = false,
            length = 64,
            columnDefinition = "char(64)",
            updatable = false
    )
    private String requestHash;

    @Column(name = "target_timer_run_id", updatable = false)
    private UUID targetTimerRunId;

    @Column(name = "target_study_record_id", updatable = false)
    private UUID targetStudyRecordId;

    @Column(name = "http_status", nullable = false, updatable = false)
    private short httpStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_code", nullable = false, length = 50, updatable = false)
    private CommandResultCode resultCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_payload", nullable = false, columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> resultPayload;

    @CreatedDate
    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    @Builder
    public CommandReceiptEntity(
            Long cohortMembershipId,
            UUID commandId,
            CommandCode commandCode,
            String requestHash,
            UUID targetTimerRunId,
            UUID targetStudyRecordId,
            short httpStatus,
            CommandResultCode resultCode,
            Map<String, Object> resultPayload
    ) {
        this.id = new CommandReceiptId(cohortMembershipId, commandId);
        this.commandCode = commandCode;
        this.requestHash = requestHash;
        this.targetTimerRunId = targetTimerRunId;
        this.targetStudyRecordId = targetStudyRecordId;
        this.httpStatus = httpStatus;
        this.resultCode = resultCode;
        this.resultPayload = resultPayload == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(resultPayload);
    }

    public Map<String, Object> getResultPayload() {
        return Collections.unmodifiableMap(resultPayload);
    }
}
