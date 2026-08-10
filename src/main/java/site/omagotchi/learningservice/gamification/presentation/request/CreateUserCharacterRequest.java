package site.omagotchi.learningservice.gamification.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import site.omagotchi.learningservice.gamification.application.command.CreateUserCharacterCommand;

public record CreateUserCharacterRequest(
        @NotNull Long gameCharacterId,
        @NotBlank @Size(max = 30) String nickname
) {

    public CreateUserCharacterCommand toCommand() {
        return new CreateUserCharacterCommand(gameCharacterId, nickname);
    }
}
