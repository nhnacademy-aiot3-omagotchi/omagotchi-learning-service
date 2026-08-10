package site.omagotchi.learningservice.user.application.result;

import site.omagotchi.learningservice.cohort.domain.Cohort;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;
import site.omagotchi.learningservice.cohort.domain.CohortStatus;

import java.time.LocalDate;

public record ApprovedCohortResult(
        Long cohortId,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        CohortStatus cohortStatus,
        CohortMembershipRole role,
        CohortMembershipStatus membershipStatus
) {

    public static ApprovedCohortResult from(CohortMembership membership, Cohort cohort) {
        return new ApprovedCohortResult(
                cohort.getId(),
                cohort.getName(),
                cohort.getStartDate(),
                cohort.getEndDate(),
                cohort.getStatus(),
                membership.getRole(),
                membership.getStatus()
        );
    }
}
