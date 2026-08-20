package site.omagotchi.learningservice.user.presentation.response;

import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;
import site.omagotchi.learningservice.cohort.domain.CohortStatus;
import site.omagotchi.learningservice.user.application.result.ApprovedCohortResult;

import java.time.LocalDate;

public record ApprovedCohortResponse(
        Long cohortId,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        CohortStatus cohortStatus,
        CohortMembershipRole role,
        CohortMembershipStatus membershipStatus
) {

    public static ApprovedCohortResponse from(ApprovedCohortResult result) {
        if (result == null) {
            return null;
        }
        return new ApprovedCohortResponse(
                result.cohortId(),
                result.name(),
                result.startDate(),
                result.endDate(),
                result.cohortStatus(),
                result.role(),
                result.membershipStatus()
        );
    }
}
