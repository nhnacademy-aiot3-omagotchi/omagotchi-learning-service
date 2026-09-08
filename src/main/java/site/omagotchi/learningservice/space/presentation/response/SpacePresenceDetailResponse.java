package site.omagotchi.learningservice.space.presentation.response;

import site.omagotchi.learningservice.space.application.result.SpacePresenceDetailResult;

import java.util.List;

public record SpacePresenceDetailResponse(
        Long spaceId,
        long totalCount,
        long cohortCount,
        long otherCohortCount,
        List<SpacePresenceOccupantResponse> occupants
) {
    public static SpacePresenceDetailResponse from(SpacePresenceDetailResult result) {
        return new SpacePresenceDetailResponse(
                result.spaceId(),
                result.totalCount(),
                result.cohortCount(),
                result.otherCohortCount(),
                result.occupants().stream()
                        .map(SpacePresenceOccupantResponse::from)
                        .toList()
        );
    }
}
