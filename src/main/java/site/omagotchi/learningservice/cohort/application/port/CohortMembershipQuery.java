package site.omagotchi.learningservice.cohort.application.port;

import site.omagotchi.learningservice.cohort.application.result.CohortMembershipSummaryResult;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;

import java.util.List;
import java.util.UUID;

public interface CohortMembershipQuery {

    List<CohortMembership> findByUserIdOrderByRequestedAtDesc(UUID userId);

    List<CohortMembershipSummaryResult> findAllAdminSummaries();

    List<UUID> findAllActiveManagerUserIds(Long cohortId);

    boolean existsActiveManager(Long cohortId);

    boolean existsActiveManagerByUserId(UUID userId);
}
