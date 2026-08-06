package site.omagotchi.learningservice.gamification.presentation.response;

import site.omagotchi.learningservice.gamification.application.result.CharacterGrowthResult;
import site.omagotchi.learningservice.gamification.domain.AdvancementStage;

public record CharacterGrowthResponse(
        String nickname,
        String displayName,
        long totalXp,
        int level,
        long currentLevelXp,
        long nextLevelRequiredXp,
        AdvancementStage advancementStage
) {

    public static CharacterGrowthResponse from(CharacterGrowthResult result) {
        return new CharacterGrowthResponse(
                result.nickname(),
                result.displayName(),
                result.totalXp(),
                result.level(),
                result.currentLevelXp(),
                result.nextLevelRequiredXp(),
                result.advancementStage()
        );
    }
}
