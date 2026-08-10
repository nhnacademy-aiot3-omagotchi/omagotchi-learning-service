package site.omagotchi.learningservice.space.presentation.response;

import site.omagotchi.learningservice.space.domain.Space;
import site.omagotchi.learningservice.space.domain.SpaceOperationalStatus;

import java.time.ZonedDateTime;

public record SpaceStatusResponse(
        Long id,
        SpaceOperationalStatus operationalStatus,
        String inactiveReason,
        ZonedDateTime updatedAt
) {

    public static SpaceStatusResponse from(Space space) {
        return new SpaceStatusResponse(
                space.getId(),
                space.getOperationalStatus(),
                space.getInactiveReason(),
                space.getUpdatedAt()
        );
    }
}
