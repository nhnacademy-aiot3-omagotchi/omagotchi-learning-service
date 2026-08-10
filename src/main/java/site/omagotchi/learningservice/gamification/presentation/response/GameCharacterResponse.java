package site.omagotchi.learningservice.gamification.presentation.response;

import site.omagotchi.learningservice.gamification.application.result.GameCharacterResult;

public record GameCharacterResponse(
        Long gameCharacterId,
        String code,
        String name,
        String description
) {

    public static GameCharacterResponse from(GameCharacterResult result) {
        return new GameCharacterResponse(
                result.gameCharacterId(),
                result.code(),
                result.name(),
                result.description()
        );
    }
}
