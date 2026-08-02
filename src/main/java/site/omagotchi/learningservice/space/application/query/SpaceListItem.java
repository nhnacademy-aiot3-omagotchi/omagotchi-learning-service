package site.omagotchi.learningservice.space.application.query;

import site.omagotchi.learningservice.space.domain.SpaceOperationalStatus;
import site.omagotchi.learningservice.space.domain.SpaceType;
import site.omagotchi.learningservice.space.domain.SpaceUsageStatus;

import java.time.ZonedDateTime;

/**
 * 공간 목록 조회 결과 모델.
 *
 * 조회 전용 모델이므로 도메인 객체와 분리한다.
 */
public record SpaceListItem(
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

    public SpaceListItem(
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
                remainingTimeSeconds
        );
    }
}
