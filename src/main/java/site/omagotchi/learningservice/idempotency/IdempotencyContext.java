package site.omagotchi.learningservice.idempotency;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record IdempotencyContext(
        @NotNull
        UUID idempotencyKey,
        @NotNull
        UUID scopeId
) {
}

