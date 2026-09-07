package site.omagotchi.learningservice.space.presentation.response;

import site.omagotchi.learningservice.space.application.result.SpacePresenceOccupantResult;

import java.util.UUID;

public record SpacePresenceOccupantResponse(
        UUID userId,
        String displayName
) {
    public static SpacePresenceOccupantResponse from(SpacePresenceOccupantResult result) {
        return new SpacePresenceOccupantResponse(result.userId(), result.displayName());
    }
}
