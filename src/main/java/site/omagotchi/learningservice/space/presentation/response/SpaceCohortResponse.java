package site.omagotchi.learningservice.space.presentation.response;

import site.omagotchi.learningservice.space.domain.Space;

import java.time.ZonedDateTime;

public record SpaceCohortResponse(
        Long id,
        Long cohortId,
        ZonedDateTime updatedAt
) {

    public static SpaceCohortResponse from(Space space) {
        return new SpaceCohortResponse(
                space.getId(),
                space.getCohortId(),
                space.getUpdatedAt()
        );
    }
}
