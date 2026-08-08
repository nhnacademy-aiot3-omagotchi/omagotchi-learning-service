package site.omagotchi.learningservice.user.presentation.response;

import site.omagotchi.learningservice.user.application.result.CurrentCharacterResult;

public record CurrentCharacterResponse(
        String nickname,
        int level,
        long currentExp,
        long requiredExp,
        String name,
        String type,
        String assetKey
) {

    public static CurrentCharacterResponse from(CurrentCharacterResult result) {
        if (result == null) {
            return null;
        }
        return new CurrentCharacterResponse(
                result.nickname(),
                result.level(),
                result.currentExp(),
                result.requiredExp(),
                result.name(),
                result.type(),
                result.assetKey()
        );
    }
}
