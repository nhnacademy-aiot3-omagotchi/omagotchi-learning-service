package site.omagotchi.learningservice.space.presentation.response;

import site.omagotchi.learningservice.space.domain.Space;
import site.omagotchi.learningservice.space.domain.SpaceType;

import java.time.ZonedDateTime;

public record UpdateSpaceResponse(
        Long id,
        String name,
        SpaceType type,
        Integer capacity,
        ZonedDateTime updatedAt
) {

    public static UpdateSpaceResponse from(
            Space space
    ) {
        return new UpdateSpaceResponse(
                space.getId(),
                space.getName(),
                space.getSpaceType(),
                space.getCapacity(),
                space.getUpdatedAt()
        );
    }
}
