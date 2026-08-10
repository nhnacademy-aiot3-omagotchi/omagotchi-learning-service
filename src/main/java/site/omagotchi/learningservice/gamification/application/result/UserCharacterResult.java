package site.omagotchi.learningservice.gamification.application.result;

import site.omagotchi.learningservice.gamification.domain.AdvancementStage;
import site.omagotchi.learningservice.gamification.domain.GameCharacter;
import site.omagotchi.learningservice.gamification.domain.UserCharacter;

public record UserCharacterResult(
        Long userCharacterId,
        Long gameCharacterId,
        String gameCharacterCode,
        String gameCharacterName,
        String nickname,
        String displayName,
        long totalXp,
        int level,
        AdvancementStage advancementStage,
        boolean representative
) {

    public static UserCharacterResult from(UserCharacter userCharacter, GameCharacter gameCharacter) {
        return new UserCharacterResult(
                userCharacter.getId(),
                userCharacter.getGameCharacterId(),
                gameCharacter.getCode(),
                gameCharacter.getName(),
                userCharacter.getNickname(),
                userCharacter.displayName(),
                userCharacter.getTotalXp(),
                userCharacter.getLevel(),
                userCharacter.getAdvancementStage(),
                userCharacter.isRepresentative()
        );
    }
}
