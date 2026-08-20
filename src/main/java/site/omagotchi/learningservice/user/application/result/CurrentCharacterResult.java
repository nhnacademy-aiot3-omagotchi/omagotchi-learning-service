package site.omagotchi.learningservice.user.application.result;

public record CurrentCharacterResult(
        String nickname,
        int level,
        long currentExp,
        long requiredExp,
        String name,
        String type,
        String assetKey
) {
}
