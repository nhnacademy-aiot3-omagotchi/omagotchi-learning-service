package site.omagotchi.learningservice.space.application.port.in;

import site.omagotchi.learningservice.space.domain.Space;

public interface ActivateSpaceUseCase {

    Space activate(Long spaceId);
}
