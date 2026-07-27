package site.omagotchi.learningservice.space.application.command;

import site.omagotchi.learningservice.space.domain.SpaceType;

public record CreateSpaceCommand(
        String name,
        SpaceType spaceType,
        Integer capacity
) {
}
