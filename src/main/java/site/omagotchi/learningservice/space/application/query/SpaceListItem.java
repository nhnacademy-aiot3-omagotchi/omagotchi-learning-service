package site.omagotchi.learningservice.space.application.query;

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
        SpaceType type,
        Integer capacity,
        SpaceUsageStatus status,
        ZonedDateTime occupancyExpiresAt,
        Long remainingTimeSeconds
) {
}