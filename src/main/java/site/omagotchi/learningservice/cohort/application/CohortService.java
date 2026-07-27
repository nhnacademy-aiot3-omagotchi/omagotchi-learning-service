package site.omagotchi.learningservice.cohort.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.dto.command.ChangeCohortStatusCommand;
import site.omagotchi.learningservice.cohort.application.dto.command.CreateCohortCommand;
import site.omagotchi.learningservice.cohort.application.dto.command.UpdateCohortCommand;
import site.omagotchi.learningservice.cohort.application.dto.result.CohortResponse;
import site.omagotchi.learningservice.cohort.domain.Cohort;
import site.omagotchi.learningservice.cohort.domain.CohortErrorCode;
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
    private final CohortAccessService accessService;

    /**
     * 새 기수를 PREPARING 상태로 생성한다.
     * 생성자는 JWT 연동 전까지 임시 userId를 전달받아 createdByUserId에 저장한다.
     */
    @Transactional
    public CohortResponse create(CreateCohortCommand command, Long userId, String globalRole) {
        accessService.requireSystemAdmin(globalRole);

        Cohort cohort = Cohort.create(
                command.name(),
                command.description(),
                command.startDate(),
                command.endDate(),
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
    public CohortResponse update(Long cohortId, UpdateCohortCommand command, Long actorUserId) {
        accessService.requireManager(cohortId, actorUserId);

        Cohort cohort = getCohortOrThrow(cohortId);

        cohort.updateBasicInfo(
                command.name(),
                command.description(),
                command.startDate(),
                command.endDate()
        );
        return CohortResponse.from(cohort);
    }

    /**
     * 기수 상태를 PREPARING에서 ACTIVE로, ACTIVE에서 CLOSED로 전환한다.
     * ACTIVE 전환 시 활성 MANAGER 소속이 최소 1명 있어야 한다.
     */
    @Transactional
    public CohortResponse changeStatus(Long cohortId, ChangeCohortStatusCommand command, String globalRole) {
        accessService.requireSystemAdmin(globalRole);

        Cohort cohort = getCohortOrThrow(cohortId);

        if (command.status() == CohortStatus.ACTIVE) {
            if (cohort.getStatus() != CohortStatus.PREPARING) {
                throw new BusinessException(CohortErrorCode.INVALID_COHORT_STATUS_TRANSITION);
            }
            if (!membershipRepository.existsActiveManagerByCohortId(cohortId)) {
                throw new BusinessException(CohortErrorCode.COHORT_ACTIVE_MANAGER_REQUIRED);
            }

            cohort.activate(true);
            return CohortResponse.from(cohort);
        }

        if (command.status() == CohortStatus.CLOSED) {
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
