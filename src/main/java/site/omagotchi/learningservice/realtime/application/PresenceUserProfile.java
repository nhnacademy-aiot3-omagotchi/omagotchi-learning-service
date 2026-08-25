package site.omagotchi.learningservice.realtime.application;

public record PresenceUserProfile(
        String nickname,
        PresenceCharacterSnapshot currentCharacter
) {
}
