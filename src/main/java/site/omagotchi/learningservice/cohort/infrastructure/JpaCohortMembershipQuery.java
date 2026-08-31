package site.omagotchi.learningservice.cohort.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.cohort.application.port.CohortMembershipQuery;
import site.omagotchi.learningservice.cohort.application.result.CohortMembershipSummaryResult;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaCohortMembershipQuery implements CohortMembershipQuery {

    private final CohortMembershipRepository repository;

    @Override
    public List<CohortMembership> findByUserIdOrderByRequestedAtDesc(UUID userId) {
        return repository.findByUserIdOrderByRequestedAtDesc(userId);
    }

    @Override
    public List<CohortMembershipSummaryResult> findAllAdminSummaries() {
        Map<Long, Long> memberCounts = repository.countActiveMembershipsByCohort().stream()
                .collect(java.util.stream.Collectors.toMap(
                        CohortMembershipRepository.CohortMembershipCountProjection::getCohortId,
                        CohortMembershipRepository.CohortMembershipCountProjection::getMemberCount
                ));
        Map<Long, List<UUID>> managerUserIds = new HashMap<>();
        repository.findAllActiveManagersByCohort().forEach(manager ->
                managerUserIds.computeIfAbsent(manager.getCohortId(), ignored -> new ArrayList<>())
                        .add(manager.getUserId()));

        return memberCounts.entrySet().stream()
                .map(entry -> new CohortMembershipSummaryResult(
                        entry.getKey(),
                        entry.getValue(),
                        managerUserIds.getOrDefault(entry.getKey(), List.of())
                ))
                .toList();
    }

    @Override
    public List<UUID> findAllActiveManagerUserIds(Long cohortId) {
        return repository.findAllByCohortIdAndRoleAndStatusOrderByRequestedAtAsc(
                        cohortId,
                        CohortMembershipRole.MANAGER,
                        CohortMembershipStatus.ACTIVE
                ).stream()
                .map(membership -> membership.getUserId())
                .toList();
    }

    @Override
    public boolean existsActiveManager(Long cohortId) {
        return repository.existsActiveManagerByCohortId(cohortId);
    }

    @Override
    public boolean existsActiveManagerByUserId(UUID userId) {
        return repository.existsByUserIdAndRoleAndStatusAndEndedAtIsNull(
                userId,
                CohortMembershipRole.MANAGER,
                CohortMembershipStatus.ACTIVE
        );
    }
}
