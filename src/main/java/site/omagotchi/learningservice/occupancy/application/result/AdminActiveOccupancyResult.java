package site.omagotchi.learningservice.occupancy.application.result;

import site.omagotchi.learningservice.occupancy.domain.OccupancyStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminActiveOccupancyResult(
        Long spaceId,
        String spaceName,
        Long occupancyId,
        UUID occupierUserId,
        String occupierDisplayName,
        int participantCount,
        OffsetDateTime startedAt,
        OffsetDateTime expiresAt,
        long remainingTimeSeconds,
        OccupancyStatus status
) {
}
