package site.omagotchi.learningservice.space.application.port.in;

import site.omagotchi.learningservice.space.domain.Space;

import java.util.UUID;

public interface UnassignLabCohortUseCase {

    Space unassignCohort(
            Long spaceId,
            UUID actorUserId,
            String globalRole
    );
}
