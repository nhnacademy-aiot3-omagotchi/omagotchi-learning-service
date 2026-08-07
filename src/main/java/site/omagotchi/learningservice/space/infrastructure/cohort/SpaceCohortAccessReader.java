package site.omagotchi.learningservice.space.infrastructure.cohort;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import site.omagotchi.learningservice.cohort.application.CohortAccessService;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;
import site.omagotchi.learningservice.cohort.domain.CohortStatus;
import site.omagotchi.learningservice.cohort.infrastructure.CohortMembershipRepository;
import site.omagotchi.learningservice.cohort.infrastructure.CohortRepository;
import site.omagotchi.learningservice.global.auth.GlobalRole;
import site.omagotchi.learningservice.global.exception.BusinessException;
import site.omagotchi.learningservice.space.application.port.SpaceCohortAccessPort;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SpaceCohortAccessReader implements SpaceCohortAccessPort {

    private final CohortAccessService cohortAccessService;
    private final CohortRepository cohortRepository;
    private final CohortMembershipRepository membershipRepository;

    @Override
    public boolean exists(Long cohortId) {
        return cohortRepository.existsById(cohortId);
    }

    @Override
    public boolean isSystemAdmin(GlobalRole globalRole) {
        try {
            cohortAccessService.requireSystemAdmin(globalRole);
            return true;
        } catch (BusinessException exception) {
            return false;
        }
    }

    @Override
    public boolean isActiveManager(Long cohortId, UUID userId) {
        try {
            cohortAccessService.requireManager(cohortId, userId);
            return true;
        } catch (BusinessException exception) {
            return false;
        }
    }

    @Override
    public List<Long> findActiveManagedCohortIds(UUID userId) {
        Set<Long> activeCohortIds = cohortRepository
                .findByStatus(CohortStatus.ACTIVE)
                .stream()
                .map(cohort -> cohort.getId())
                .collect(Collectors.toSet());

        return membershipRepository
                .findByUserIdOrderByRequestedAtDesc(userId)
                .stream()
                .filter(membership ->
                        membership.getRole() == CohortMembershipRole.MANAGER
                )
                .filter(membership ->
                        membership.getStatus() == CohortMembershipStatus.ACTIVE
                )
                .map(membership -> membership.getCohortId())
                .filter(activeCohortIds::contains)
                .distinct()
                .toList();
    }

    @Override
    public List<Long> findActiveCohortIds(UUID userId) {
        return membershipRepository
                .findByUserIdOrderByRequestedAtDesc(userId)
                .stream()
                .filter(membership ->
                        membership.getStatus() == CohortMembershipStatus.ACTIVE
                )
                .filter(membership -> membership.getEndedAt() == null)
                .map(membership -> membership.getCohortId())
                .distinct()
                .toList();
    }
}
