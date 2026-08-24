package site.omagotchi.learningservice.gamification.application.result;

import site.omagotchi.learningservice.gamification.domain.GameCharacter;

public record GameCharacterResult(
        Long gameCharacterId,
        String code,
        String assetKey,
        String name,
        String description
) {

    public static GameCharacterResult from(GameCharacter gameCharacter) {
        return new GameCharacterResult(
                gameCharacter.getId(),
                gameCharacter.getCode(),
                gameCharacter.getAssetKey(),
                gameCharacter.getName(),
                gameCharacter.getDescription()
        );
    }
}
