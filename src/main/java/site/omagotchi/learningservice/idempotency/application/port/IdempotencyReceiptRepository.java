package site.omagotchi.learningservice.idempotency.application.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyReceiptRepository {

    boolean tryClaim(
            UUID scopeId,
            UUID idempotencyKey,
            String operationCode,
            String requestHash,
            Instant expiresAt
    );

    Optional<IdempotencyReceipt> find(
            UUID scopeId,
            UUID idempotencyKey
    );

    void complete(
            UUID scopeId,
            UUID idempotencyKey,
            String responseJson
    );

    int deleteExpired(Instant now);
}

