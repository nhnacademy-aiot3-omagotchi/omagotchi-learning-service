package site.omagotchi.learningservice.cohort.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.dto.command.SaveAttendancePolicyRequest;
import site.omagotchi.learningservice.cohort.application.dto.result.CohortAttendancePolicyResponse;
import site.omagotchi.learningservice.cohort.domain.CohortAttendancePolicy;
import site.omagotchi.learningservice.cohort.infrastructure.CohortAttendancePolicyRepository;
import site.omagotchi.learningservice.cohort.infrastructure.CohortRepository;
import site.omagotchi.learningservice.global.exception.BusinessException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CohortAttendancePolicyService {

    private final CohortRepository cohortRepository;
    private final CohortAttendancePolicyRepository attendancePolicyRepository;

    /**
     * 특정 기수의 출결 정책을 조회
     * 출결/타이머 기능이 기수별 판정 기준을 가져갈 때 사용
     */
    public CohortAttendancePolicyResponse getPolicy(Long cohortId) {
        return attendancePolicyRepository.findById(cohortId)
                .map(CohortAttendancePolicyResponse::from)
                .orElseThrow(() -> new BusinessException(CohortErrorCode.COHORT_NOT_FOUND));
    }

    /**
     * 기수별 출결 정책을 생성하거나 이미 있으면 수정
     * 정책은 cohort_id를 PK로 사용하므로 한 기수에 하나만 존재
     */
    @Transactional
    public CohortAttendancePolicyResponse savePolicy(
            Long cohortId,
            SaveAttendancePolicyRequest request,
            Long updatedByUserId
    ) {
        if (!cohortRepository.existsById(cohortId)) {
            throw new BusinessException(CohortErrorCode.COHORT_NOT_FOUND);
        }

        CohortAttendancePolicy policy = attendancePolicyRepository.findById(cohortId)
                .map(existingPolicy -> updateExistingPolicy(existingPolicy, request, updatedByUserId))
                .orElseGet(() -> createPolicy(cohortId, request, updatedByUserId));

        return CohortAttendancePolicyResponse.from(attendancePolicyRepository.save(policy));
    }

    private CohortAttendancePolicy updateExistingPolicy(
            CohortAttendancePolicy policy,
            SaveAttendancePolicyRequest request,
            Long updatedByUserId
    ) {
        policy.update(
                request.timezone(),
                request.scheduledStartTime(),
                request.scheduledEndTime(),
                request.absenceCutoffTime(),
                request.allowedAwayMinutes(),
                updatedByUserId
        );
        return policy;
    }

    private CohortAttendancePolicy createPolicy(
            Long cohortId,
            SaveAttendancePolicyRequest request,
            Long updatedByUserId
    ) {
        return CohortAttendancePolicy.create(
                cohortId,
                request.timezone(),
                request.scheduledStartTime(),
                request.scheduledEndTime(),
                request.absenceCutoffTime(),
                request.allowedAwayMinutes(),
                updatedByUserId
        );
    }
}
