package site.omagotchi.learningservice.cohort.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.domain.*;
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

    /**
     * 사용자가 해당 기수의 종료되지 않은 ACTIVE STUDENT인지 확인하고 소속 식별자를 반환한다.
     */
    public Long requireActiveStudentMembershipId(Long cohortId, UUID userId) {
        CohortMembership membership = requireActiveMembership(cohortId, userId);
        if (membership.getRole() != CohortMembershipRole.STUDENT
                || membership.getEndedAt() != null) {
            throw new BusinessException(CohortErrorCode.COHORT_ACCESS_DENIED);
        }
        return membership.getId();
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
     * 출결 정책 경계 전용 검사. 전역 관리자 또는 해당 기수 매니저를 허용한다.
     *
     * <p>{@link #requireManager}는 기수의 ACTIVE 소속을 먼저 요구하므로, 기수에 소속되지
     * 않는 SYSTEM_ADMIN은 통과하지 못한다. 그런데 출결 정책은 기수 생성 직후 반드시
     * 채워져야 하고 그 시점에는 매니저가 아직 없을 수 있어, 이 경계에서만 전역 관리자를
     * 허용한다.</p>
     *
     * <p>{@link #requireManager}는 28곳에서 쓰이므로 건드리지 않는다. 이 메서드는
     * {@link CohortAttendancePolicyService}에서만 호출한다. 다른 기수 기능의 권한 모델은
     * 그대로다.</p>
     *
     * <p>전역 관리자라도 없는 기수는 통과시키지 않는다. 존재하지 않는 cohortId로 정책이
     * 저장되면 FK 위반이 뒤늦게 터진다.</p>
     */
    public void requireAttendancePolicyEditor(Long cohortId, UUID userId, GlobalRole globalRole) {
        if (globalRole == GlobalRole.SYSTEM_ADMIN) {
            if (!cohortRepository.existsById(cohortId)) {
                throw new BusinessException(CohortErrorCode.COHORT_NOT_FOUND);
            }
            return;
        }
        requireManager(cohortId, userId);
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
                .map(Cohort::getId)
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
