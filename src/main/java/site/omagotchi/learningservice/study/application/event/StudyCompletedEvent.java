package site.omagotchi.learningservice.study.application.event;

import java.time.Instant;
import java.util.UUID;

/**
 * 사용자 행동 하나로 유효한 학습 기록이 확정되었다.
 *
 * @param sourceId 수동 기록이면 studyRecordId, 타이머 종료이면 timerRunId
 */
public record StudyCompletedEvent(
        UUID userId,
        UUID sourceId,
        Instant occurredAt
) {
}
