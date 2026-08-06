package site.omagotchi.learningservice.gamification.application.command;

public record CreateUserCharacterCommand(
        Long gameCharacterId,
        String nickname
) {
}
