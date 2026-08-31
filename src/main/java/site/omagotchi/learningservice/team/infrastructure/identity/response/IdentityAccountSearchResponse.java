package site.omagotchi.learningservice.team.infrastructure.identity.response;

import site.omagotchi.learningservice.team.application.port.IdentityAccountState;

import java.util.UUID;

public record IdentityAccountSearchResponse(
        UUID accountId,
        String displayName,
        String email,
        IdentityAccountState status
) {
}
