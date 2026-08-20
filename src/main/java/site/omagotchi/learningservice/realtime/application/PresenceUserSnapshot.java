package site.omagotchi.learningservice.realtime.application;

import java.util.UUID;

public record PresenceUserSnapshot(
        UUID userId,
        PresenceStatus status
) {
}
