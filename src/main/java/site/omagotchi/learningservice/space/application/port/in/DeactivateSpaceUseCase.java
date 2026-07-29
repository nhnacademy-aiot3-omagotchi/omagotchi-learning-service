package site.omagotchi.learningservice.space.application.port.in;

import site.omagotchi.learningservice.space.domain.Space;

public interface DeactivateSpaceUseCase {

    Space deactivate(
            Long spaceId,
            String reason
    );
}
