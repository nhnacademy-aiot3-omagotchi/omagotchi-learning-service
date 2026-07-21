package site.omagotchi.learningservice.cohort.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;
import site.omagotchi.learningservice.cohort.infrastructure.CohortMembershipRepository;
import site.omagotchi.learningservice.global.exception.BusinessException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CohortAccessService {

    private final CohortMembershipRepository membershipRepository;

    /**
     * 사용자가 해당 기수의 ACTIVE 소속인지 확인하고, 활성 소속 정보를 반환
     * ACTIVE 소속이 없으면 기수 존재를 숨기기 위해 404로 처리
     */
    public CohortMembership requireActiveMembership(Long cohortId, Long userId) {
        return membershipRepository
                .findFirstByCohortIdAndUserIdAndStatusOrderByRequestedAtDesc(
                        cohortId,
                        userId,
                        CohortMembershipStatus.ACTIVE
                )
                .orElseThrow(() -> new BusinessException(CohortErrorCode.COHORT_NOT_FOUND));
    }

    /**
     * 사용자가 해당 기수에서 MANAGER 역할의 ACTIVE 소속인지 확인
     * 소속은 있지만 관리자 역할이 아니면 403으로 처리
     */
    public void requireManager(Long cohortId, Long userId) {
        requireActiveMembership(cohortId, userId);

        boolean isManager = membershipRepository.existsByCohortIdAndUserIdAndRoleAndStatus(
                cohortId,
                userId,
                CohortMembershipRole.MANAGER,
                CohortMembershipStatus.ACTIVE
        );
        if (!isManager) {
            throw new BusinessException(CohortErrorCode.COHORT_MANAGER_REQUIRED);
        }
    }

    /**
     * 사용자가 해당 기수의 ACTIVE 소속인지 boolean으로 확인
     * 예외를 던지지 않는 단순 조건 분기용
     */
    public boolean isActiveMember(Long cohortId, Long userId) {
        return membershipRepository
                .findFirstByCohortIdAndUserIdAndStatusOrderByRequestedAtDesc(
                        cohortId,
                        userId,
                        CohortMembershipStatus.ACTIVE
                )
                .isPresent();
    }
}
