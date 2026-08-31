package site.omagotchi.learningservice.cohort.application.port;

import site.omagotchi.learningservice.cohort.application.result.CohortMembershipSummaryResult;
import site.omagotchi.learningservice.cohort.application.result.CohortMembershipView;
import java.util.Collection;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface CohortMembershipQuery {

    List<CohortMembership> findByUserIdOrderByRequestedAtDesc(UUID userId);

    List<CohortMembershipSummaryResult> findAllAdminSummaries();
    List<UUID> findAllActiveManagerUserIds(Long cohortId);
    boolean existsActiveManager(Long cohortId);
    boolean existsActiveManagerByUserId(UUID userId);
    List<CohortMembershipView> findActiveStudents(Long cohortId);
    List<CohortMembershipView> findActiveByCohortId(Long cohortId);
    Optional<CohortMembershipView> findByIdAndActive(Long membershipId);
    Optional<CohortMembershipView> findByCohortIdAndUserIdAndActive(Long cohortId, UUID userId);
    Map<UUID, CohortMembershipView> findActiveByCohortIdAndUserIds(Long cohortId, Collection<UUID> userIds);
    List<CohortMembershipView> findActiveByUserId(UUID userId);
    List<Long> findIdsByCohortId(Long cohortId);
    Map<Long, UUID> findUserIds(Collection<Long> membershipIds);
    Map<Long, Long> findCohortIds(Collection<Long> membershipIds);
    Set<Long> findInactiveIds(Collection<Long> membershipIds);
}
