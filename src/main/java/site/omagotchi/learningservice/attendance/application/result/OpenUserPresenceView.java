package site.omagotchi.learningservice.attendance.application.result;

import java.time.Instant;
import java.util.UUID;

public record OpenUserPresenceView(
        UUID userId,
        Long attendanceId,
        Long cohortMembershipId,
        Instant startedAt
) {
    public OpenPresenceView toPresenceView() {
        return new OpenPresenceView(attendanceId, cohortMembershipId, startedAt);
    }
}
