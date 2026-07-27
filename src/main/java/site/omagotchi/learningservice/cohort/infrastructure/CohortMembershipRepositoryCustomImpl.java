package site.omagotchi.learningservice.cohort.infrastructure;

import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;

import java.util.Optional;
import java.util.UUID;

import static site.omagotchi.learningservice.cohort.domain.QCohortMembership.cohortMembership;

@RequiredArgsConstructor
public class CohortMembershipRepositoryCustomImpl implements CohortMembershipRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    /*
     * SELECT cm.id
     * FROM learning_service.cohort_memberships cm
     * WHERE cm.user_id = :userId
     *   AND cm.cohort_id = :cohortId
     *   AND cm.status = 'ACTIVE';
     */
    @Override
    public Optional<Long> findActiveMembershipId(UUID userId, Long cohortId) {
        Long membershipId = queryFactory
                .select(cohortMembership.id)
                .from(cohortMembership)
                .where(
                        cohortMembership.userId.eq(userId),
                        cohortMembership.cohortId.eq(cohortId),
                        cohortMembership.status.eq(CohortMembershipStatus.ACTIVE)
                )
                .fetchOne();

        return Optional.ofNullable(membershipId);
    }
}
