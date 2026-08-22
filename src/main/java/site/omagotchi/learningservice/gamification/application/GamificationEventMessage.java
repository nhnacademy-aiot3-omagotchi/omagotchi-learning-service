package site.omagotchi.learningservice.gamification.application;

import java.time.Instant;
import java.util.UUID;

public record GamificationEventMessage(
        GamificationEventType eventType,
        String sourceId,
        UUID userId,
        Instant occurredAt
) {
}
