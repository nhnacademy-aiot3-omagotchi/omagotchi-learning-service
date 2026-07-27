package site.omagotchi.learningservice.cohort.infrastructure;

import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static site.omagotchi.learningservice.cohort.domain.QCohortMembership.cohortMembership;

@RequiredArgsConstructor
public class CohortMembershipRepositoryCustomImpl implements CohortMembershipRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<MembershipView> findActiveAll(UUID userId) {
        return queryFactory
                .select(
                        cohortMembership.id,
                        cohortMembership.cohortId,
                        cohortMembership.userId
                )
                .from(cohortMembership)
                .where(
                        cohortMembership.userId.eq(userId),
                        cohortMembership.status.eq(CohortMembershipStatus.ACTIVE)
                )
                .fetch()
                .stream()
                .map(tuple -> new MembershipView(
                        tuple.get(cohortMembership.id),
                        tuple.get(cohortMembership.cohortId),
                        tuple.get(cohortMembership.userId)
                ))
                .toList();
    }

    @Override
    public Map<Long, UUID> findUserIds(Collection<Long> membershipIds) {
        if (membershipIds == null || membershipIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, UUID> userIds = new LinkedHashMap<>();
        queryFactory
                .select(cohortMembership.id, cohortMembership.userId)
                .from(cohortMembership)
                .where(cohortMembership.id.in(membershipIds))
                .fetch()
                .forEach(tuple -> userIds.put(
                        tuple.get(cohortMembership.id),
                        tuple.get(cohortMembership.userId)
                ));
        return userIds;
    }

    @Override
    public Optional<MembershipView> findActive(Long cohortId, UUID userId) {
        var tuple = queryFactory
                .select(
                        cohortMembership.id,
                        cohortMembership.cohortId,
                        cohortMembership.userId
                )
                .from(cohortMembership)
                .where(
                        cohortMembership.cohortId.eq(cohortId),
                        cohortMembership.userId.eq(userId),
                        cohortMembership.status.eq(CohortMembershipStatus.ACTIVE)
                )
                .fetchOne();

        if (tuple == null) {
            return Optional.empty();
        }

        return Optional.of(new MembershipView(
                tuple.get(cohortMembership.id),
                tuple.get(cohortMembership.cohortId),
                tuple.get(cohortMembership.userId)
        ));
    }

    /*
     * SELECT cm.id
     * FROM learning_service.cohort_memberships cm
     * WHERE cm.user_id = :userId
     *   AND cm.cohort_id = :cohortId
     *   AND cm.status = 'ACTIVE';
     */
    @Override
    public Optional<Long> findActiveMembershipId(UUID userId, Long cohortId) {
        return findActive(cohortId, userId).map(MembershipView::id);
    }
}
