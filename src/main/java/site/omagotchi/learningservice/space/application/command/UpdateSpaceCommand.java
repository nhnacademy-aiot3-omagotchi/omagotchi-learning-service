package site.omagotchi.learningservice.space.application.command;

import site.omagotchi.learningservice.space.domain.SpaceType;

public record UpdateSpaceCommand(
        String name,
        SpaceType type,
        Integer capacity
) {
}