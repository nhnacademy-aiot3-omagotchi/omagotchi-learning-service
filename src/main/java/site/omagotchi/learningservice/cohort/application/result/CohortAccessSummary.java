package site.omagotchi.learningservice.cohort.application.result;

import site.omagotchi.learningservice.cohort.domain.Cohort;
import site.omagotchi.learningservice.cohort.domain.CohortStatus;

import java.time.LocalDate;

/**
 * 화면 접근과 기수 선택에 필요한 최소 기수 정보.
 */
public record CohortAccessSummary(
        Long cohortId,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        CohortStatus status
) {
    public static CohortAccessSummary from(Cohort cohort) {
        return new CohortAccessSummary(
                cohort.getId(),
                cohort.getName(),
                cohort.getStartDate(),
                cohort.getEndDate(),
                cohort.getStatus()
        );
    }
}
