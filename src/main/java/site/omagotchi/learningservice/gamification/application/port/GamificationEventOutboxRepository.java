package site.omagotchi.learningservice.gamification.application.port;

import site.omagotchi.learningservice.gamification.application.GamificationEventMessage;
import site.omagotchi.learningservice.gamification.application.GamificationEventType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GamificationEventOutboxRepository {

    void enqueue(GamificationEventMessage event);

    Optional<OutboxEvent> lockPending(EventKey key, Instant now);

    List<EventKey> findRetryable(Instant now, int limit);

    void markCompleted(Long outboxId, Instant processedAt);

    void recordFailure(EventKey key, Instant nextAttemptAt, String errorMessage);

    record EventKey(GamificationEventType eventType, String sourceId) {
    }

    record OutboxEvent(Long id, GamificationEventMessage message) {
    }
}
