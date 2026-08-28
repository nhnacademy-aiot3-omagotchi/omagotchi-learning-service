package site.omagotchi.learningservice.occupancy.application.result;

import java.util.UUID;

public record OccupancyParticipantResult(
        UUID userId,
        String displayName,
        boolean occupier
) {
}
