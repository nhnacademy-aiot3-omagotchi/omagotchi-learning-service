package site.omagotchi.learningservice.gamification.application.port;

import site.omagotchi.learningservice.gamification.application.GamificationEventType;

import java.time.Instant;
import java.util.UUID;

/**
 * 같은 원천 이벤트가 퀘스트 진행을 두 번 변경하지 않도록 처리 권한을 원자적으로 선점한다.
 */
public interface GamificationEventReceiptRepository {

    boolean claim(
            GamificationEventType eventType,
            String sourceId,
            UUID userId,
            Instant occurredAt
    );
}
