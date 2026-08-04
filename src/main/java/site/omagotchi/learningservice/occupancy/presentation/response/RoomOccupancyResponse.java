package site.omagotchi.learningservice.occupancy.presentation.response;

import site.omagotchi.learningservice.occupancy.application.result.RoomOccupancyResult;

import java.time.OffsetDateTime;

/**
 * 점유 응답.
 *
 * <p>{@code remainingSeconds}를 함께 내려주는 것은 클라이언트가 서버 시계와 자기 시계의
 * 차이를 신경 쓰지 않게 하기 위해서다. {@code expiresAt}만 주면 단말 시계가 틀어졌을 때
 * 남은 시간이 어긋난다.</p>
 *
 * <p>점유자·참여자 정보를 담지 않는다 (MR-36).</p>
 */
public record RoomOccupancyResponse(
        Long occupancyId,
        Long spaceId,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime expiresAt,
        int extensionCount,
        long remainingSeconds
) {

    public static RoomOccupancyResponse from(RoomOccupancyResult result) {
        return new RoomOccupancyResponse(
                result.occupancyId(),
                result.spaceId(),
                result.status().name(),
                result.startedAt(),
                result.expiresAt(),
                result.extensionCount(),
                result.remainingSeconds()
        );
    }
}