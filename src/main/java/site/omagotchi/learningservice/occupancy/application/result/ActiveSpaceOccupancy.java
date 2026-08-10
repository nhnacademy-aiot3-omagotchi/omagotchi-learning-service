package site.omagotchi.learningservice.occupancy.application.result;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * {@code room_occupancies} 한 행에서 바로 읽어낼 수 있는 활성 점유 정보.
 *
 * <p>{@link SpaceOccupancyView}와 나눠 둔 이유는 출처가 다르기 때문이다. 이 레코드는
 * 점유 테이블 조회 한 번으로 완성되지만, 저쪽은 여기에 기수 파트의 조회 결과
 * (점유자 기수)와 참여자 테이블 조회 결과를 더해야 만들어진다. 하나로 합치면 Port가
 * 채울 수 없는 필드를 가진 반쯤 빈 객체가 오가고, 그것을 그대로 다른 Feature에
 * 넘기는 실수가 컴파일러에 걸리지 않는다.</p>
 *
 * <p>Port가 반환하는 Application Type이며 다른 Feature에 노출하지 않는다 —
 * 공개 계약은 {@link SpaceOccupancyView} 하나다.</p>
 *
 * @param expiresAt 이 점유의 만료 시각. 조회 조건이 {@code expiresAt > now}이므로 항상 미래다
 */
public record ActiveSpaceOccupancy(
        Long occupancyId,
        Long spaceId,
        OffsetDateTime expiresAt,
        Long occupierMembershipId,
        UUID occupierUserId
) {
}
