package site.omagotchi.learningservice.attendance.presentation.response;

import site.omagotchi.learningservice.attendance.application.result.CurrentPresenceResult;
import site.omagotchi.learningservice.attendance.domain.PresenceState;

import java.time.Instant;

public record CurrentPresenceResponse(
        Long spaceId,
        PresenceState state,
        Instant startedAt
) {

    public static CurrentPresenceResponse from(CurrentPresenceResult result) {
        return new CurrentPresenceResponse(
                result.spaceId(),
                result.state(),
                result.startedAt()
        );
    }
}
