package site.omagotchi.learningservice.space.application.port.in;

import site.omagotchi.learningservice.space.domain.Space;

import java.util.UUID;

public interface ActivateSpaceUseCase {

    Space activate(
            Long spaceId,
            UUID actorUserId,
            String globalRole
    );
}
