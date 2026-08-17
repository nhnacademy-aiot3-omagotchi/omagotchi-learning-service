package site.omagotchi.learningservice.cohort.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.domain.CohortErrorCode;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;
import site.omagotchi.learningservice.cohort.domain.CohortStatus;
import site.omagotchi.learningservice.cohort.infrastructure.CohortMembershipRepository;
import site.omagotchi.learningservice.cohort.infrastructure.CohortRepository;
import site.omagotchi.learningservice.global.auth.GlobalRole;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CohortAccessService {

    private final CohortMembershipRepository membershipRepository;
    private final CohortRepository cohortRepository;

    public boolean exists(Long cohortId) {
        return cohortRepository.existsById(cohortId);
    }

    /**
     * 전역 시스템 관리자 권한이 필요한 작업인지 확인
     */
    public void requireSystemAdmin(GlobalRole globalRole) {
        if (globalRole != GlobalRole.SYSTEM_ADMIN) {
            throw new BusinessException(CohortErrorCode.SYSTEM_ADMIN_REQUIRED);
        }
    }

    /**
     * 사용자가 해당 기수의 ACTIVE 소속인지 확인하고, 활성 소속 정보를 반환
     * ACTIVE 소속이 없으면 기수 존재를 숨기기 위해 404로 처리
     */
    public CohortMembership requireActiveMembership(Long cohortId, UUID userId) {
        return membershipRepository
                .findFirstByCohortIdAndUserIdAndStatusOrderByRequestedAtDesc(
                        cohortId,
                        userId,
                        CohortMembershipStatus.ACTIVE
                )
                .orElseThrow(() -> new BusinessException(CohortErrorCode.COHORT_NOT_FOUND));
    }

    /**
     * 사용자가 해당 기수의 ACTIVE 소속인지 확인하고, 소속 식별자를 반환
     * ACTIVE 소속이 없으면 기수 존재를 숨기기 위해 404로 처리
     */
    public Long requireActiveMembershipId(Long cohortId, UUID userId) {
        return membershipRepository.findActiveMembershipId(userId, cohortId)
                .orElseThrow(() -> new BusinessException(CohortErrorCode.COHORT_NOT_FOUND));
    }

    public CohortMembership requireCurrentActiveMembership(UUID userId) {
        return membershipRepository.findFirstByUserIdAndStatusAndEndedAtIsNullOrderByRequestedAtDesc(
                userId,
                CohortMembershipStatus.ACTIVE
        ).orElseThrow(() -> new BusinessException(CohortErrorCode.COHORT_NOT_FOUND));
    }

    /**
     * 사용자가 해당 기수에서 MANAGER 역할의 ACTIVE 소속인지 확인
     * 소속은 있지만 관리자 역할이 아니면 403으로 처리
     */
    public void requireManager(Long cohortId, UUID userId) {
        requireActiveMembershipId(cohortId, userId);

        if (!isManager(cohortId, userId)) {
            throw new BusinessException(CohortErrorCode.COHORT_MANAGER_REQUIRED);
        }
    }

    /**
     * 사용자가 해당 기수에서 MANAGER 역할의 ACTIVE 소속인지 boolean으로 확인
     * 예외를 던지지 않는 단순 조건 분기용
     *
     * <p>소속이 아예 없는 경우와 소속은 있으나 매니저가 아닌 경우를 구분하지 않는다.
     * 둘을 나눠 404와 403으로 응답해야 하면 {@link #requireManager}를 쓴다.</p>
     */
    public boolean isManager(Long cohortId, UUID userId) {
        return membershipRepository.existsByCohortIdAndUserIdAndRoleAndStatus(
                cohortId,
                userId,
                CohortMembershipRole.MANAGER,
                CohortMembershipStatus.ACTIVE
        );
    }

    /**
     * 사용자가 해당 기수의 ACTIVE 소속인지 boolean으로 확인
     * 예외를 던지지 않는 단순 조건 분기용
     */
    public boolean isActiveMember(Long cohortId, UUID userId) {
        return membershipRepository
                .findFirstByCohortIdAndUserIdAndStatusOrderByRequestedAtDesc(
                        cohortId,
                        userId,
                        CohortMembershipStatus.ACTIVE
                )
                .isPresent();
    }

    public List<Long> findActiveManagedCohortIds(UUID userId) {
        return findActiveCohortIds(userId, CohortMembershipRole.MANAGER);
    }

    public List<Long> findActiveCohortIds(UUID userId) {
        return findActiveCohortIds(userId, null);
    }

    private List<Long> findActiveCohortIds(
            UUID userId,
            CohortMembershipRole requiredRole
    ) {
        Set<Long> activeCohortIds = cohortRepository
                .findByStatus(CohortStatus.ACTIVE)
                .stream()
                .map(cohort -> cohort.getId())
                .collect(Collectors.toSet());

        return membershipRepository
                .findByUserIdOrderByRequestedAtDesc(userId)
                .stream()
                .filter(membership -> requiredRole == null
                        || membership.getRole() == requiredRole)
                .filter(membership ->
                        membership.getStatus() == CohortMembershipStatus.ACTIVE
                )
                .filter(membership -> membership.getEndedAt() == null)
                .map(CohortMembership::getCohortId)
                .filter(activeCohortIds::contains)
                .distinct()
                .toList();
    }
}
