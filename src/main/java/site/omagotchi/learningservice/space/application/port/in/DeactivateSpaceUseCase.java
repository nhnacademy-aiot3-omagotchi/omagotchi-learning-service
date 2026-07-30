package site.omagotchi.learningservice.space.application.port.in;

import site.omagotchi.learningservice.space.domain.Space;

import java.util.UUID;

public interface DeactivateSpaceUseCase {

    Space deactivate(
            Long spaceId,
            String reason,
            UUID actorUserId,
            String globalRole
    );
}
