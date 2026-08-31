package site.omagotchi.learningservice.occupancy.presentation.response;

import site.omagotchi.learningservice.occupancy.application.result.AdminActiveOccupancyResult;
import site.omagotchi.learningservice.occupancy.domain.OccupancyStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminActiveOccupancyResponse(
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
    public static AdminActiveOccupancyResponse from(AdminActiveOccupancyResult result) {
        return new AdminActiveOccupancyResponse(
                result.spaceId(), result.spaceName(), result.occupancyId(),
                result.occupierUserId(), result.occupierDisplayName(), result.participantCount(),
                result.startedAt(), result.expiresAt(), result.remainingTimeSeconds(), result.status());
    }
}
