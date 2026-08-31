package site.omagotchi.learningservice.occupancy.presentation.response;

import site.omagotchi.learningservice.occupancy.application.result.OccupancyParticipantResult;

import java.util.UUID;

public record OccupancyParticipantResponse(
        UUID userId,
        String displayName,
        boolean occupier
) {
    public static OccupancyParticipantResponse from(OccupancyParticipantResult result) {
        return new OccupancyParticipantResponse(
                result.userId(), result.displayName(), result.occupier());
    }
}
