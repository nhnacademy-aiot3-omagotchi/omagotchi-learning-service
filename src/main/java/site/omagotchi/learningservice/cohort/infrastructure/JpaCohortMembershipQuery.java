package site.omagotchi.learningservice.cohort.infrastructure;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import site.omagotchi.learningservice.cohort.application.port.CohortMembershipQuery;
import site.omagotchi.learningservice.cohort.application.result.CohortMembershipSummaryResult;
import site.omagotchi.learningservice.cohort.application.result.CohortMembershipView;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

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

    @Override public List<CohortMembershipView> findActiveStudents(Long id) { return repository.findActiveStudents(id).stream().map(CohortMembershipView::from).toList(); }
    @Override public List<CohortMembershipView> findActiveByCohortId(Long id) { return repository.findByCohortIdAndStatusOrderByRequestedAtAsc(id, CohortMembershipStatus.ACTIVE).stream().map(CohortMembershipView::from).toList(); }
    @Override public Optional<CohortMembershipView> findByIdAndActive(Long id) { return repository.findByIdAndStatus(id, CohortMembershipStatus.ACTIVE).map(CohortMembershipView::from); }
    @Override public Optional<CohortMembershipView> findByCohortIdAndUserIdAndActive(Long cohortId, UUID userId) { return repository.findFirstByCohortIdAndUserIdAndStatusOrderByRequestedAtDesc(cohortId,userId,CohortMembershipStatus.ACTIVE).map(CohortMembershipView::from); }
    @Override public Map<UUID,CohortMembershipView> findActiveByCohortIdAndUserIds(Long id, Collection<UUID> ids) { return repository.findByCohortIdAndUserIdInAndStatus(id,ids,CohortMembershipStatus.ACTIVE).stream().map(CohortMembershipView::from).collect(java.util.stream.Collectors.toMap(CohortMembershipView::userId,v->v)); }
    @Override public List<CohortMembershipView> findActiveByUserId(UUID id) { return repository.findByUserIdOrderByRequestedAtDesc(id).stream().filter(m->m.getStatus()==CohortMembershipStatus.ACTIVE).map(CohortMembershipView::from).toList(); }
    @Override public List<Long> findIdsByCohortId(Long id) { return repository.findByCohortId(id).stream().map(CohortMembership::getId).toList(); }
    @Override public Map<Long,UUID> findUserIds(Collection<Long> ids) { return repository.findAllById(ids).stream().collect(java.util.stream.Collectors.toMap(CohortMembership::getId,CohortMembership::getUserId)); }
    @Override public Map<Long,Long> findCohortIds(Collection<Long> ids) { return repository.findAllById(ids).stream().collect(java.util.stream.Collectors.toMap(CohortMembership::getId,CohortMembership::getCohortId)); }
    @Override public Set<Long> findInactiveIds(Collection<Long> ids) { return repository.findAllById(ids).stream().filter(m->m.getStatus()!=CohortMembershipStatus.ACTIVE).map(CohortMembership::getId).collect(java.util.stream.Collectors.toUnmodifiableSet()); }

    @Override
    public boolean existsActiveManagerByUserId(UUID userId) {
        return repository.existsByUserIdAndRoleAndStatusAndEndedAtIsNull(
                userId,
                CohortMembershipRole.MANAGER,
                CohortMembershipStatus.ACTIVE
        );
    }
}
