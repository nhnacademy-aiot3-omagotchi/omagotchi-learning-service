package site.omagotchi.learningservice.gamification.application.result;

import site.omagotchi.learningservice.gamification.domain.AdvancementStage;
import site.omagotchi.learningservice.gamification.domain.LevelState;
import site.omagotchi.learningservice.gamification.domain.UserCharacter;

public record CharacterGrowthResult(
        Long userCharacterId,
        String nickname,
        String displayName,
        long totalXp,
        int level,
        long currentLevelXp,
        long nextLevelRequiredXp,
        AdvancementStage advancementStage
) {

    public static CharacterGrowthResult from(UserCharacter character, LevelState levelState) {
        return new CharacterGrowthResult(
                character.getId(),
                character.getNickname(),
                character.displayName(),
                character.getTotalXp(),
                levelState.level(),
                levelState.currentLevelXp(),
                levelState.nextLevelRequiredXp(),
                levelState.advancementStage()
        );
    }
}
