package site.omagotchi.learningservice.space.presentation.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import site.omagotchi.learningservice.space.application.command.CreateSpaceCommand;
import site.omagotchi.learningservice.space.domain.SpaceType;

public record CreateSpaceRequest(

        @NotBlank
        String name,

        @NotNull
        SpaceType type,

        @NotNull
        @Min(1)
        Integer capacity,

        @Min(1)
        Long cohortId
) {

    public CreateSpaceCommand toCommand() {
        return new CreateSpaceCommand(
                name,
                type,
                capacity,
                cohortId
        );
    }
}
