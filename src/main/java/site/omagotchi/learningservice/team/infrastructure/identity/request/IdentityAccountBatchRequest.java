package site.omagotchi.learningservice.team.infrastructure.identity.request;

import java.util.List;
import java.util.UUID;

public record IdentityAccountBatchRequest(
        List<UUID> accountIds
) {
}
