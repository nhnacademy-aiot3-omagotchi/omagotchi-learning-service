package site.omagotchi.learningservice.space.presentation.response;

import site.omagotchi.learningservice.space.domain.Space;
import site.omagotchi.learningservice.space.domain.SpaceType;

import java.time.ZonedDateTime;

public record CreateSpaceResponse(
        Long id,
        String name,
        SpaceType type,
        Integer capacity,
        Long cohortId,
        ZonedDateTime createdAt
) {

    public static CreateSpaceResponse from(Space space) {
        return new CreateSpaceResponse(
                space.getId(),
                space.getName(),
                space.getSpaceType(),
                space.getCapacity(),
                space.getCohortId(),
                space.getCreatedAt()
        );
    }
}
