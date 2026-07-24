package site.omagotchi.learningservice.space.application.port.in;

import site.omagotchi.learningservice.space.application.command.CreateSpaceCommand;
import site.omagotchi.learningservice.space.domain.Space;

public interface CreateSpaceUseCase {

    Space create(CreateSpaceCommand command);
}