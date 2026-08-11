package site.omagotchi.learningservice.occupancy.application.result;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 다른 Feature에 노출하는 "이 회의실이 지금 사용 중" 정보.
 *
 * <p>공간 목록이 사용 상태를 파생 계산하는 데 쓴다 (명세서 01, SSOT 원칙). 공간은
 * 사용 여부를 컬럼으로 저장하지 않고 활성 점유의 존재로 매 조회 시 판단한다.</p>
 *
 * <p><b>점유자·참여자 식별자를 담되, 노출 여부는 판정하지 않는다</b> (MR-36).
 * 같은 기수에만 보여주는 것은 화면 정책이라 소비처가 소유한다 — 공간 목록은
 * 요청자의 기수를 알지만 점유는 모르기 때문이다. 여기서 걸러 내보내려면 요청자
 * 기수를 인자로 받아야 하는데, 그러면 소비처가 늘 때마다 이 계약에 화면별 조건이
 * 하나씩 붙는다.</p>
 *
 * <p><b>받는 쪽 책임</b>: {@code occupierCohortId}를 요청자의 기수와 대조하고,
 * 일치하지 않으면 {@code occupierUserId}·{@code occupierMembershipId}·
 * {@code participantUserIds}를 응답에서 제외해야 한다. 이 셋은 타 기수 사용자에게
 * 노출되면 안 되는 개인정보다.</p>
 *
 * <p>남은 시간을 담지 않는 것은 의도다. 받는 쪽이 표시 시간대를 정해야 하고(공간 목록은
 * {@code ZonedDateTime}으로 내보낸다), 회의실이 아닌 공간은 아예 값이 없어야 하는 등
 * 표현 규칙이 소비처마다 다르다. 여기서는 만료 시각이라는 사실만 준다.</p>
 *
 * @param spaceId             사용 중인 회의실
 * @param expiresAt           이 점유의 만료 시각
 * @param occupierCohortId    점유자의 기수. 소비처의 노출 판정 기준이다
 * @param participantUserIds  이탈하지 않은 참여자. 점유자 본인도 포함된다 (MR-27).
 *                            참여 순서({@code occupancy_participants.id})를 유지한다
 */
public record SpaceOccupancyView(
        Long occupancyId,
        Long spaceId,
        OffsetDateTime expiresAt,
        Long occupierCohortId,
        Long occupierMembershipId,
        UUID occupierUserId,
        List<UUID> participantUserIds
) {

    /**
     * 점유 행에 기수와 참여자를 붙여 공개 계약을 만든다.
     *
     * @param occupierCohortId   멤버십이 이미 지워졌으면 {@code null}일 수 있다. 그때는
     *                           어느 기수와도 일치하지 않아 소비처가 개인정보를 감춘다 —
     *                           판정할 수 없으면 감추는 쪽이 안전한 기본값이다
     * @param participantUserIds 방어적 복사 대상이다. 호출부가 넘긴 목록을 그대로 들고
     *                           있으면 다른 Feature가 참여자 목록을 바꿀 수 있다
     */
    public static SpaceOccupancyView of(
            ActiveSpaceOccupancy occupancy,
            Long occupierCohortId,
            List<UUID> participantUserIds
    ) {
        return new SpaceOccupancyView(
                occupancy.occupancyId(),
                occupancy.spaceId(),
                occupancy.expiresAt(),
                occupierCohortId,
                occupancy.occupierMembershipId(),
                occupancy.occupierUserId(),
                participantUserIds == null ? List.of() : List.copyOf(participantUserIds)
        );
    }
}
