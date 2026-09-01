package site.omagotchi.learningservice.cohort.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.port.CohortPersistence;
import site.omagotchi.learningservice.cohort.application.result.CohortLockView;
import site.omagotchi.learningservice.cohort.domain.Cohort;
import site.omagotchi.learningservice.cohort.domain.CohortStatus;
import site.omagotchi.learningservice.global.exception.BusinessException;

/**
 * 기수 상태와 실습실 구성을 함께 바꾸는 흐름의 잠금 경계.
 *
 * <p>기수 행을 먼저 잠그고 공간 행을 잠그는 순서를 공통 계약으로 사용한다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CohortLockService {

    private final CohortPersistence cohortPersistence;

    @Transactional
    public CohortLockView lock(Long cohortId) {
        Cohort cohort = lockCohort(cohortId);

        return new CohortLockView(
                cohort.getId(),
                cohort.getStatus() == CohortStatus.ACTIVE
        );
    }

    /** 같은 feature의 상태 변경 서비스가 사용하는 공통 잠금 경로. */
    Cohort lockCohort(Long cohortId) {
        return cohortPersistence.findByIdForUpdate(cohortId)
                .orElseThrow(() -> new BusinessException(CohortErrorCode.COHORT_NOT_FOUND));
    }
}
