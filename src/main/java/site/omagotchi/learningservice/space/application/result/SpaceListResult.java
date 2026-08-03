package site.omagotchi.learningservice.space.application.result;

import site.omagotchi.learningservice.space.domain.SpaceOperationalStatus;
import site.omagotchi.learningservice.space.domain.SpaceType;
import site.omagotchi.learningservice.space.domain.SpaceUsageStatus;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 공간 목록 조회 결과 모델.
 *
 * 조회 전용 모델이므로 도메인 객체와 분리한다.
 */
public record SpaceListResult(
        Long spaceId,
        String name,
        SpaceType spaceType,
        Integer capacity,
        SpaceOperationalStatus operationalStatus,
        String inactiveReason,
        Long cohortId,
        SpaceUsageStatus status,
        ZonedDateTime occupancyExpiresAt,
        Long remainingTimeSeconds,
        Long occupancyCohortId,
        Long occupierMembershipId,
        UUID occupierUserId,
        List<UUID> participantUserIds
) {

    public SpaceListResult(
            Long spaceId,
            String name,
            SpaceType spaceType,
            Integer capacity,
            SpaceOperationalStatus operationalStatus,
            String inactiveReason,
            Long cohortId,
            SpaceUsageStatus status,
            ZonedDateTime occupancyExpiresAt,
            Long remainingTimeSeconds
    ) {
        this(
                spaceId, name, spaceType, capacity,
                operationalStatus, inactiveReason, cohortId, status,
                occupancyExpiresAt, remainingTimeSeconds,
                null, null, null, null
        );
    }

    public SpaceListResult(
            Long spaceId,
            String name,
            SpaceType spaceType,
            Integer capacity,
            SpaceUsageStatus status,
            ZonedDateTime occupancyExpiresAt,
            Long remainingTimeSeconds
    ) {
        this(
                spaceId,
                name,
                spaceType,
                capacity,
                null,
                null,
                null,
                status,
                occupancyExpiresAt,
                remainingTimeSeconds,
                null,
                null,
                null,
                null
        );
    }
}
