package site.omagotchi.learningservice.realtime.application;

import java.time.OffsetDateTime;
import java.util.List;

public record CohortPresenceSnapshot(
        Long cohortId,
        List<PresenceUserSnapshot> users,
        OffsetDateTime occurredAt
) {
    public CohortPresenceSnapshot {
        users = users == null ? List.of() : List.copyOf(users);
    }
}
