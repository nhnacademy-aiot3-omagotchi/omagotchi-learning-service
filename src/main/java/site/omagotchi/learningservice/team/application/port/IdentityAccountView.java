package site.omagotchi.learningservice.team.application.port;

import java.util.UUID;

public record IdentityAccountView(
        UUID accountId,
        String displayName,
        String email,
        IdentityAccountState status
) {
}
