package site.omagotchi.learningservice.cohort.application.result;

/**
 * 공간 정책이 기수 행을 잠근 뒤 필요한 최소 상태만 노출한다.
 */
public record CohortLockView(
        Long cohortId,
        boolean active
) {
}
