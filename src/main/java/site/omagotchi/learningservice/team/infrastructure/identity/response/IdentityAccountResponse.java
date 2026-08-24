package site.omagotchi.learningservice.team.infrastructure.identity.response;

import site.omagotchi.learningservice.team.application.port.IdentityAccountState;

import java.util.UUID;

public record IdentityAccountResponse(
        UUID accountId,
        String displayName,
        IdentityAccountState status
) {
}
