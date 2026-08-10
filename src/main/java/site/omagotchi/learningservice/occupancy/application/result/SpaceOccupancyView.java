package site.omagotchi.learningservice.occupancy.application.result;

import java.time.OffsetDateTime;

/**
 * 다른 Feature에 노출하는 "이 회의실이 지금 사용 중" 정보.
 *
 * <p>공간 목록이 사용 상태를 파생 계산하는 데 쓴다 (명세서 01, SSOT 원칙). 공간은
 * 사용 여부를 컬럼으로 저장하지 않고 활성 점유의 존재로 매 조회 시 판단한다.</p>
 *
 * <p><b>점유자·참여자 정보를 담지 않는다</b> (MR-36). 공간 목록은 모든 사용자가 보는
 * 화면이라 "누가 쓰고 있는지"가 들어가면 타 기수 사용자의 개인정보가 노출된다.</p>
 *
 * <p>남은 시간을 담지 않는 것도 의도다. 받는 쪽이 표시 시간대를 정해야 하고(공간 목록은
 * {@code ZonedDateTime}으로 내보낸다), 회의실이 아닌 공간은 아예 값이 없어야 하는 등
 * 표현 규칙이 소비처마다 다르다. 여기서는 만료 시각이라는 사실만 준다.</p>
 *
 * @param spaceId   사용 중인 회의실
 * @param expiresAt 이 점유의 만료 시각
 */
public record SpaceOccupancyView(
        Long spaceId,
        OffsetDateTime expiresAt
) {
}
