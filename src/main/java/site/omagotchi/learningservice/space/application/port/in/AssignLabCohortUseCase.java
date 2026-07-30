package site.omagotchi.learningservice.space.application.port.in;

import site.omagotchi.learningservice.space.domain.Space;

import java.util.UUID;

public interface AssignLabCohortUseCase {

    Space assignCohort(
            Long spaceId,
            Long cohortId,
            UUID actorUserId,
            String globalRole
    );
}
