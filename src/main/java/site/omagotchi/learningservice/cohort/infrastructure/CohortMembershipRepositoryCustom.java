package site.omagotchi.learningservice.cohort.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface CohortMembershipRepositoryCustom {

    List<MembershipView> findActiveAll(UUID userId);

    Map<Long, UUID> findUserIds(Collection<Long> membershipIds);

    Optional<MembershipView> findActive(Long cohortId, UUID userId);

    /**
     * 사용자와 기수가 일치하는 ACTIVE 소속의 식별자를 조회한다.
     *
     * @param userId 사용자 식별자
     * @param cohortId 기수 식별자
     * @return 활성 소속 식별자, 없으면 {@link Optional#empty()}
     */
    Optional<Long> findActiveMembershipId(UUID userId, Long cohortId);

    record MembershipView(Long id, Long cohortId, UUID userId) {
    }
}
