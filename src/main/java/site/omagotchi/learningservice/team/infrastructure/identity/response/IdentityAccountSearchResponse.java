package site.omagotchi.learningservice.team.infrastructure.identity.response;

import site.omagotchi.learningservice.team.application.port.IdentityAccountState;

import java.time.Instant;
import java.util.UUID;

public record IdentityAccountSearchResponse(
        UUID accountId,
        String displayName,
        String email,
        IdentityAccountState status,
        Instant statusChangedAt
) {
}
