package site.omagotchi.learningservice.realtime.application;

import java.util.UUID;

public record PresenceUserSnapshot(
        UUID userId,
        String nickname,
        PresenceCharacterSnapshot currentCharacter,
        PresenceStatus status
) {
    public PresenceUserSnapshot(UUID userId, PresenceStatus status) {
        this(userId, null, null, status);
    }
}
