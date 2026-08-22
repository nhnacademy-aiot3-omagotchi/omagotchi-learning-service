package site.omagotchi.learningservice.gamification.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.gamification.application.GamificationEventType;
import site.omagotchi.learningservice.gamification.application.port.GamificationEventReceiptRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JdbcGamificationEventReceiptRepository implements GamificationEventReceiptRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public boolean claim(
            GamificationEventType eventType,
            String sourceId,
            UUID userId,
            Instant occurredAt
    ) {
        int inserted = jdbcTemplate.update("""
                INSERT INTO learning_service.gamification_event_receipts
                    (event_type, source_id, user_id, occurred_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (event_type, source_id) DO NOTHING
                """, eventType.name(), sourceId, userId, Timestamp.from(occurredAt));
        return inserted == 1;
    }
}
