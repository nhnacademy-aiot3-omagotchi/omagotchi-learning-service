package site.omagotchi.learningservice.gamification.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.gamification.application.GamificationEventMessage;
import site.omagotchi.learningservice.gamification.application.GamificationEventType;
import site.omagotchi.learningservice.gamification.application.port.GamificationEventOutboxRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JdbcGamificationEventOutboxRepository implements GamificationEventOutboxRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void enqueue(GamificationEventMessage event) {
        jdbcTemplate.update("""
                INSERT INTO learning_service.gamification_event_outbox
                    (event_type, source_id, user_id, occurred_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (event_type, source_id) DO NOTHING
                """,
                event.eventType().name(),
                event.sourceId(),
                event.userId(),
                Timestamp.from(event.occurredAt()));
    }

    @Override
    public Optional<OutboxEvent> lockPending(EventKey key, Instant now) {
        return jdbcTemplate.query("""
                SELECT id, event_type, source_id, user_id, occurred_at
                  FROM learning_service.gamification_event_outbox
                 WHERE event_type = ?
                   AND source_id = ?
                   AND status = 'PENDING'
                   AND next_attempt_at <= ?
                 FOR UPDATE SKIP LOCKED
                """,
                (resultSet, rowNumber) -> new OutboxEvent(
                        resultSet.getLong("id"),
                        new GamificationEventMessage(
                                GamificationEventType.valueOf(resultSet.getString("event_type")),
                                resultSet.getString("source_id"),
                                resultSet.getObject("user_id", java.util.UUID.class),
                                resultSet.getTimestamp("occurred_at").toInstant())),
                key.eventType().name(),
                key.sourceId(),
                Timestamp.from(now)
        ).stream().findFirst();
    }

    @Override
    public List<EventKey> findRetryable(Instant now, int limit) {
        return jdbcTemplate.query("""
                SELECT event_type, source_id
                  FROM learning_service.gamification_event_outbox
                 WHERE status = 'PENDING'
                   AND next_attempt_at <= ?
                 ORDER BY next_attempt_at, id
                 LIMIT ?
                """,
                (resultSet, rowNumber) -> new EventKey(
                        GamificationEventType.valueOf(resultSet.getString("event_type")),
                        resultSet.getString("source_id")),
                Timestamp.from(now),
                limit);
    }

    @Override
    public void markCompleted(Long outboxId, Instant processedAt) {
        jdbcTemplate.update("""
                UPDATE learning_service.gamification_event_outbox
                   SET status = 'COMPLETED',
                       processed_at = ?,
                       last_error = NULL,
                       updated_at = ?
                 WHERE id = ?
                   AND status = 'PENDING'
                """,
                Timestamp.from(processedAt),
                Timestamp.from(processedAt),
                outboxId);
    }

    @Override
    public void recordFailure(EventKey key, Instant nextAttemptAt, String errorMessage) {
        jdbcTemplate.update("""
                UPDATE learning_service.gamification_event_outbox
                   SET attempt_count = attempt_count + 1,
                       next_attempt_at = ?,
                       last_error = ?,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE event_type = ?
                   AND source_id = ?
                   AND status = 'PENDING'
                """,
                Timestamp.from(nextAttemptAt),
                errorMessage,
                key.eventType().name(),
                key.sourceId());
    }
}
