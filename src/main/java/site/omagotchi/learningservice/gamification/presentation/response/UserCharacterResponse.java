package site.omagotchi.learningservice.gamification.presentation.response;

import site.omagotchi.learningservice.gamification.application.result.UserCharacterResult;
import site.omagotchi.learningservice.gamification.domain.AdvancementStage;

public record UserCharacterResponse(
        Long userCharacterId,
        Long gameCharacterId,
        String gameCharacterCode,
        String type,
        String colorId,
        String assetKey,
        String gameCharacterName,
        String nickname,
        String displayName,
        long totalXp,
        int level,
        AdvancementStage advancementStage,
        boolean representative
) {

    public static UserCharacterResponse from(UserCharacterResult result) {
        return new UserCharacterResponse(
                result.userCharacterId(),
                result.gameCharacterId(),
                result.gameCharacterCode(),
                result.type(),
                result.colorId(),
                result.assetKey(),
                result.gameCharacterName(),
                result.nickname(),
                result.displayName(),
                result.totalXp(),
                result.level(),
                result.advancementStage(),
                result.representative()
        );
    }
}
