package site.omagotchi.learningservice.space.application.port.in;

import site.omagotchi.learningservice.space.application.command.UpdateSpaceCommand;
import site.omagotchi.learningservice.space.domain.Space;

public interface UpdateSpaceUseCase {

    Space update(
            Long spaceId,
            UpdateSpaceCommand command
    );
}