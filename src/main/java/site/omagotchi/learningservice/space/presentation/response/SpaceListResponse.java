package site.omagotchi.learningservice.space.presentation.response;

import site.omagotchi.learningservice.space.application.query.SpaceListItem;
import site.omagotchi.learningservice.space.domain.SpaceOperationalStatus;
import site.omagotchi.learningservice.space.domain.SpaceType;
import site.omagotchi.learningservice.space.domain.SpaceUsageStatus;

import java.time.ZonedDateTime;

/**
 * 공간 목록 및 현재 사용 상태 API 응답.
 */
public record SpaceListResponse(
        Long spaceId,
        String name,
        SpaceType type,
        Integer capacity,
        SpaceOperationalStatus operationalStatus,
        String inactiveReason,
        Long cohortId,
        SpaceUsageStatus status,
        ZonedDateTime occupancyExpiresAt,
        Long remainingTimeSeconds
) {

    /**
     * Application 계층의 조회 결과를 API 응답 객체로 변환한다.
     */
    public static SpaceListResponse from(
            SpaceListItem item
    ) {
        return new SpaceListResponse(
                item.spaceId(),
                item.name(),
                item.spaceType(),
                item.capacity(),
                item.operationalStatus(),
                item.inactiveReason(),
                item.cohortId(),
                item.status(),
                item.occupancyExpiresAt(),
                item.remainingTimeSeconds()
        );
    }
}
