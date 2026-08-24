package site.omagotchi.learningservice.realtime.application;

public record PresenceCharacterSnapshot(
        String type,
        String colorId,
        String assetKey
) {
}
