package site.omagotchi.learningservice.cohort.application.result;

import site.omagotchi.learningservice.cohort.domain.CohortMembership;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * 종료 정리가 잠근 ENDED 소속의 최소 정보.
 *
 * <p>{@link CohortMembershipView}는 ACTIVE 소속 조회 계약이므로 종료 정리에 재사용하지
 * 않는다. {@code endedAt}은 후속 기능이 발견 시각이 아니라 실제 소속 종료 시각으로
 * 이력을 닫는 데 사용하는 정본이다.</p>
 *
 * @param membershipId 종료된 소속 식별자
 * @param endedAt       실제 소속 종료 시각
 */
public record EndedMembershipLockView(
        Long membershipId,
        OffsetDateTime endedAt
) {

    public EndedMembershipLockView {
        Objects.requireNonNull(membershipId, "소속 ID는 필수입니다.");
        Objects.requireNonNull(endedAt, "종료 시각은 필수입니다.");
    }

    public static EndedMembershipLockView from(CohortMembership membership) {
        return new EndedMembershipLockView(
                membership.getId(),
                membership.getEndedAt()
        );
    }
}
