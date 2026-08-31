package site.omagotchi.learningservice.attendance.application.result;

import java.time.Instant;
import java.util.UUID;

public record OpenUserPresenceView(
        UUID userId,
        Long cohortMembershipId,
        Instant startedAt
) {
    public OpenPresenceView toPresenceView() {
        return new OpenPresenceView(cohortMembershipId, startedAt);
    }
}
