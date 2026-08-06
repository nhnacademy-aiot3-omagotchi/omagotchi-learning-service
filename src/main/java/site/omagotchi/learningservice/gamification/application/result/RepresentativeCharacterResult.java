package site.omagotchi.learningservice.gamification.application.result;

import site.omagotchi.learningservice.gamification.domain.UserCharacter;

import java.util.UUID;

public record RepresentativeCharacterResult(
        UUID userId,
        Long userCharacterId,
        String displayName
) {

    public static RepresentativeCharacterResult from(UserCharacter character) {
        return new RepresentativeCharacterResult(
                character.getUserId(),
                character.getId(),
                character.displayName()
        );
    }
}
