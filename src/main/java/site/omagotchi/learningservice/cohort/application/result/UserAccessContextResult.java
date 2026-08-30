package site.omagotchi.learningservice.cohort.application.result;

import site.omagotchi.learningservice.global.auth.GlobalRole;

import java.util.List;

/**
 * Frontend BFF가 로그인 후 이동과 페이지 접근을 같은 기준으로 판단하는 계약.
 */
public record UserAccessContextResult(
        GlobalRole globalRole,
        UserAccessType accessType,
        List<CohortAccessSummary> managedCohorts,
        List<CohortAccessSummary> studentCohorts
) {
    public UserAccessContextResult {
        managedCohorts = List.copyOf(managedCohorts);
        studentCohorts = List.copyOf(studentCohorts);
    }
}
