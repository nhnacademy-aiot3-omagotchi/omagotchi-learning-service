package site.omagotchi.learningservice.cohort.application.result;

import java.util.List;
import java.util.UUID;

public record CohortMembershipSummaryResult(
        Long cohortId,
        long memberCount,
        List<UUID> managerUserIds
) {
    public CohortMembershipSummaryResult {
        managerUserIds = List.copyOf(managerUserIds);
    }
}
