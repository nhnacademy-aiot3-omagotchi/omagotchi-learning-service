package site.omagotchi.learningservice.cohort.application.result;

import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;

/**
 * 사용자가 운영 권한을 가진 기수 한 건이다.
 *
 * <p>{@code role}은 현재 MANAGER만 반환되지만, 이후 운영 역할이 늘어날 때
 * 응답 계약을 바꾸지 않도록 필드로 유지한다.</p>
 */
public record ManagedCohortResult(
        Long cohortId,
        String cohortName,
        CohortMembershipRole role
) {
}
