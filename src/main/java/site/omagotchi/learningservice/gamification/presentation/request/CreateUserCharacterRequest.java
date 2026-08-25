package site.omagotchi.learningservice.gamification.presentation.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import site.omagotchi.learningservice.gamification.application.command.CreateUserCharacterCommand;

public record CreateUserCharacterRequest(
        @NotNull Long gameCharacterId,
        @NotBlank @Size(min = 2, max = 12) String nickname,
        @Pattern(regexp = "original|pistachio|cyan|cream_can|light_coral|light_purple|white|dark_gray")
        String colorId
) {

    public CreateUserCharacterCommand toCommand() {
        return new CreateUserCharacterCommand(gameCharacterId, nickname, colorId);
    }
}
