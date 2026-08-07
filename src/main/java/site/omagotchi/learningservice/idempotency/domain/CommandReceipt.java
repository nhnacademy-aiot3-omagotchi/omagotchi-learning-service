package site.omagotchi.learningservice.idempotency.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_receipts")
@Getter
@Immutable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class CommandReceipt {

    private static final int COMMAND_CODE_MAX_LENGTH = 40;
    private static final int RESULT_CODE_MAX_LENGTH = 100;
    private static final int REQUEST_HASH_LENGTH = 64;

    @EmbeddedId
    private CommandReceiptId id;

    @Column(name = "command_code", nullable = false, length = 40, updatable = false)
    private String commandCode;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;

    @Column(name = "http_status", nullable = false, updatable = false)
    private short httpStatus;

    @Column(name = "result_code", nullable = false, length = 100, updatable = false)
    private String resultCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_payload", columnDefinition = "jsonb", updatable = false)
    private String resultPayload;

    @Column(name = "target_timer_run_id", updatable = false)
    private UUID targetTimerRunId;

    @Column(name = "target_study_record_id", updatable = false)
    private UUID targetStudyRecordId;

    @CreatedDate
    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    public static CommandReceipt create(
            CommandReceiptId id,
            String commandCode,
            String requestHash,
            short httpStatus,
            String resultCode,
            String resultPayload,
            UUID targetTimerRunId,
            UUID targetStudyRecordId
    ) {
        validateId(id);
        validateCommandCode(commandCode);
        validateRequestHash(requestHash);
        validateHttpStatus(httpStatus);
        validateResultCode(resultCode);
        validateResultPayload(resultPayload);
        validateTargetId(targetTimerRunId, targetStudyRecordId);

        CommandReceipt receipt = new CommandReceipt();
        receipt.id = id;
        receipt.commandCode = commandCode;
        receipt.requestHash = requestHash;
        receipt.httpStatus = httpStatus;
        receipt.resultCode = resultCode;
        receipt.resultPayload = resultPayload;
        receipt.targetTimerRunId = targetTimerRunId;
        receipt.targetStudyRecordId = targetStudyRecordId;

        return receipt;
    }

    private static void validateId(CommandReceiptId id) {
        if (id == null) {
            throw new IllegalArgumentException("id가 null입니다.");
        }
    }

    private static void validateCommandCode(String commandCode) {
        if (commandCode == null || commandCode.isBlank()) {
            throw new IllegalArgumentException("commandCode가 비어 있습니다.");
        }

        if (commandCode.length() > COMMAND_CODE_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "commandCode는 " + COMMAND_CODE_MAX_LENGTH + "자를 초과할 수 없습니다."
            );
        }
    }

    private static void validateRequestHash(String requestHash) {
        if (requestHash == null || requestHash.isBlank()) {
            throw new IllegalArgumentException("requestHash가 비어 있습니다.");
        }

        if (requestHash.length() != REQUEST_HASH_LENGTH) {
            throw new IllegalArgumentException(
                    "requestHash는 정확히 " + REQUEST_HASH_LENGTH + "자여야 합니다."
            );
        }

        if (!requestHash.matches("^[0-9a-fA-F]{64}$")) {
            throw new IllegalArgumentException(
                    "requestHash는 64자리 16진수 문자열이어야 합니다."
            );
        }
    }

    private static void validateHttpStatus(short httpStatus) {
        if (httpStatus < 100 || httpStatus > 599) {
            throw new IllegalArgumentException(
                    "httpStatus는 100 이상 599 이하여야 합니다."
            );
        }
    }

    private static void validateResultCode(String resultCode) {
        if (resultCode == null || resultCode.isBlank()) {
            throw new IllegalArgumentException("resultCode가 비어 있습니다.");
        }

        if (resultCode.length() > RESULT_CODE_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "resultCode는 " + RESULT_CODE_MAX_LENGTH + "자를 초과할 수 없습니다."
            );
        }
    }

    private static void validateResultPayload(String resultPayload) {
        if (resultPayload != null && resultPayload.isBlank()) {
            throw new IllegalArgumentException(
                    "resultPayload는 null이거나 유효한 JSON 문자열이어야 합니다."
            );
        }
    }

    private static void validateTargetId(
            UUID targetTimerRunId,
            UUID targetStudyRecordId
    ) {
        boolean hasTimerRunId = targetTimerRunId != null;
        boolean hasStudyRecordId = targetStudyRecordId != null;

        if (hasTimerRunId == hasStudyRecordId) {
            throw new IllegalArgumentException(
                    "targetTimerRunId와 targetStudyRecordId 중 정확히 하나만 존재해야 합니다."
            );
        }
    }
}