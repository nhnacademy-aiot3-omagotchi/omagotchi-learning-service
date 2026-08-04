package site.omagotchi.learningservice.occupancy.application.result;

import site.omagotchi.learningservice.occupancy.domain.OccupancyStatus;
import site.omagotchi.learningservice.occupancy.domain.RoomOccupancy;

import java.time.OffsetDateTime;

/**
 * 점유 조회·생성 결과.
 *
 * <p>점유자·참여자의 개인정보를 담지 않는 것이 의도다 (MR-36). 이 응답은 본인 요청에
 * 대한 것이라 노출해도 무방하지만, 같은 레코드가 목록·상세 경로에서 재사용되면
 * 타 기수 점유의 개인정보가 새어 나간다. 표시명이 필요해지면 그때 별도 결과 타입을
 * 만든다.</p>
 */
public record RoomOccupancyResult(
        Long occupancyId,
        Long spaceId,
        OccupancyStatus status,
        OffsetDateTime startedAt,
        OffsetDateTime expiresAt,
        int extensionCount,
        long remainingSeconds
) {

    public static RoomOccupancyResult of(RoomOccupancy occupancy, OffsetDateTime now) {
        return new RoomOccupancyResult(
                occupancy.getId(),
                occupancy.getSpaceId(),
                occupancy.getStatus(),
                occupancy.getStartedAt(),
                occupancy.getExpiresAt(),
                occupancy.getExtensionCount(),
                occupancy.remainingSeconds(now)
        );
    }
}
