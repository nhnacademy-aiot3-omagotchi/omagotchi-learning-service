package site.omagotchi.learningservice.cohort.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.dto.command.ChangeCohortStatusRequest;
import site.omagotchi.learningservice.cohort.application.dto.command.CreateCohortRequest;
import site.omagotchi.learningservice.cohort.application.dto.command.UpdateCohortRequest;
import site.omagotchi.learningservice.cohort.application.dto.result.CohortResponse;
import site.omagotchi.learningservice.cohort.domain.Cohort;
import site.omagotchi.learningservice.cohort.domain.CohortStatus;
import site.omagotchi.learningservice.cohort.infrastructure.CohortMembershipRepository;
import site.omagotchi.learningservice.cohort.infrastructure.CohortRepository;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CohortService {
    private final CohortRepository repository;
    private final CohortMembershipRepository membershipRepository;

    /**
     * 새 기수를 PREPARING 상태로 생성한다.
     * 생성자는 JWT 연동 전까지 임시 userId를 전달받아 createdByUserId에 저장한다.
     */
    @Transactional
    public CohortResponse create(CreateCohortRequest request, Long userId) {
        Cohort cohort = Cohort.create(
                request.name(),
                request.description(),
                request.startDate(),
                request.endDate(),
                userId
        );
        Cohort savedCohort = repository.save(cohort);
        return CohortResponse.from(savedCohort);
    }

    /**
     * 등록된 모든 기수 목록을 조회한다.
     * 관리자 대시보드의 기수 목록 화면에서 사용한다.
     */
    public List<CohortResponse> getCohorts() {
        return repository.findAll().stream()
                .map(CohortResponse::from)
                .toList();
    }

    /**
     * 단일 기수의 기본 정보와 현재 상태를 조회한다.
     * 존재하지 않는 기수는 COHORT_NOT_FOUND로 처리한다.
     */
    public CohortResponse getCohort(Long cohortId) {
        Cohort cohort = getCohortOrThrow(cohortId);
        return CohortResponse.from(cohort);
    }

    /**
     * 기수명, 설명, 운영 기간을 수정한다.
     * 종료된 기수는 도메인 규칙에 따라 수정할 수 없다.
     */
    @Transactional
    public CohortResponse update(Long cohortId, UpdateCohortRequest request) {
        Cohort cohort = getCohortOrThrow(cohortId);

        cohort.updateBasicInfo(
                request.name(),
                request.description(),
                request.startDate(),
                request.endDate()
        );
        return CohortResponse.from(cohort);
    }

    /**
     * 기수 상태를 PREPARING에서 ACTIVE로, ACTIVE에서 CLOSED로 전환한다.
     * ACTIVE 전환 시 활성 MANAGER 소속이 최소 1명 있어야 한다.
     */
    @Transactional
    public CohortResponse changeStatus(Long cohortId, ChangeCohortStatusRequest request) {
        Cohort cohort = getCohortOrThrow(cohortId);

        if (request.status() == CohortStatus.ACTIVE) {
            if (cohort.getStatus() != CohortStatus.PREPARING) {
                throw new BusinessException(CohortErrorCode.INVALID_COHORT_STATUS_TRANSITION);
            }
            if (!membershipRepository.existsActiveManagerByCohortId(cohortId)) {
                throw new BusinessException(CohortErrorCode.COHORT_ACTIVE_MANAGER_REQUIRED);
            }

            cohort.activate(true);
            return CohortResponse.from(cohort);
        }

        if (request.status() == CohortStatus.CLOSED) {
            if (cohort.getStatus() != CohortStatus.ACTIVE) {
                throw new BusinessException(CohortErrorCode.INVALID_COHORT_STATUS_TRANSITION);
            }

            cohort.close();
            return CohortResponse.from(cohort);
        }

        throw new BusinessException(CohortErrorCode.INVALID_COHORT_STATUS_TRANSITION);
    }

    private Cohort getCohortOrThrow(Long cohortId) {
        return repository.findById(cohortId)
                .orElseThrow(() -> new BusinessException(CohortErrorCode.COHORT_NOT_FOUND));
    }
}
