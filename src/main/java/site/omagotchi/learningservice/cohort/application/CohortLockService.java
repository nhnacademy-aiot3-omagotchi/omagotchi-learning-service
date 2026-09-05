package site.omagotchi.learningservice.cohort.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.port.CohortPersistence;
import site.omagotchi.learningservice.cohort.application.result.CohortLockView;
import site.omagotchi.learningservice.cohort.application.result.CohortMembershipView;
import site.omagotchi.learningservice.cohort.domain.Cohort;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;
import site.omagotchi.learningservice.cohort.domain.CohortStatus;
import site.omagotchi.learningservice.cohort.infrastructure.CohortMembershipRepository;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.util.Optional;

/**
 * 기수·소속 상태와 다른 Feature의 상태 변경을 직렬화하는 공개 잠금 경계.
 *
 * <p>기수 행을 먼저 잠그고 공간 행을 잠그는 순서를 공통 계약으로 사용한다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CohortLockService {

    private final CohortPersistence cohortPersistence;
    private final CohortMembershipRepository cohortMembershipRepository;

    @Transactional
    public CohortLockView lock(Long cohortId) {
        Cohort cohort = lockCohort(cohortId);

        return new CohortLockView(
                cohort.getId(),
                cohort.getStatus() == CohortStatus.ACTIVE
        );
    }

    /**
     * ACTIVE 소속 행을 잠그고 현재 상태를 다시 확인한다.
     *
     * <p>점유가 이 잠금을 잡고 MEETING을 만든다. 소속 종료의 UPDATE도 같은 행과
     * 충돌하므로, 점유가 먼저면 종료가 커밋을 기다리고 종료가 먼저면 이 메서드가
     * 빈 값을 반환한다.</p>
     */
    @Transactional
    public Optional<CohortMembershipView> lockActiveMembership(Long membershipId) {
        return lockMembershipEntity(membershipId, CohortMembershipStatus.ACTIVE)
                .map(CohortMembershipView::from);
    }

    /**
     * ENDED 소속 행을 잠그고 현재 상태를 다시 확인한다.
     *
     * <p>종료 후 정리는 이 잠금을 잡고 출결을 검사한다. ACTIVE writer와 같은 행을
     * 먼저 잠그는 동시에, 잘못 호출된 정리가 살아 있는 소속을 마감하지 못하게 한다.</p>
     */
    @Transactional
    public Optional<CohortMembershipView> lockEndedMembership(Long membershipId) {
        return lockMembershipEntity(membershipId, CohortMembershipStatus.ENDED)
                .map(CohortMembershipView::from);
    }

    private Optional<CohortMembership> lockMembershipEntity(
            Long membershipId,
            CohortMembershipStatus status
    ) {
        if (membershipId == null || membershipId <= 0L) {
            return Optional.empty();
        }
        return cohortMembershipRepository.findWithLockByIdAndStatus(membershipId, status);
    }

    /** 같은 feature의 상태 변경 서비스가 사용하는 공통 잠금 경로. */
    Cohort lockCohort(Long cohortId) {
        return cohortPersistence.findByIdForUpdate(cohortId)
                .orElseThrow(() -> new BusinessException(CohortErrorCode.COHORT_NOT_FOUND));
    }
}
