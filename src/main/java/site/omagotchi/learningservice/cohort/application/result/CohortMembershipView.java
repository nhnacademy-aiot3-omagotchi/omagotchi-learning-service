package site.omagotchi.learningservice.cohort.application.result;

import site.omagotchi.learningservice.cohort.domain.CohortMembership;

import java.util.UUID;

/**
 * 다른 Feature에 노출하는 기수 소속 정보.
 *
 * <p>{@code CohortMembershipResponse}와 달리 Java 표준 Type만 담는다. 저쪽은 기수 파트
 * 자신의 화면용이라 {@code CohortMembershipRole}·{@code CohortMembershipStatus}를 그대로
 * 내보내지만, 다른 Feature가 그 열거형을 받으면 cohort의 domain에 컴파일 의존이 생긴다
 * (10-backend-code-structure "공개 Application 계약").</p>
 *
 * <p>상태 필드가 없는 것은 의도다 — 이 레코드는 ACTIVE 소속만 담아 돌려주므로
 * 받는 쪽이 상태를 다시 판정할 일이 없다.</p>
 */
public record CohortMembershipView(
        Long membershipId,
        Long cohortId,
        UUID userId
) {

    public static CohortMembershipView from(CohortMembership membership) {
        return new CohortMembershipView(
                membership.getId(),
                membership.getCohortId(),
                membership.getUserId()
        );
    }
}
