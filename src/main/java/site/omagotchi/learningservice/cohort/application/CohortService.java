package site.omagotchi.learningservice.cohort.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.command.ChangeCohortStatusCommand;
import site.omagotchi.learningservice.cohort.application.command.CreateCohortCommand;
import site.omagotchi.learningservice.cohort.application.command.UpdateCohortCommand;
import site.omagotchi.learningservice.cohort.application.event.CohortClosedEvent;
import site.omagotchi.learningservice.cohort.application.port.CohortEventPublisher;
import site.omagotchi.learningservice.cohort.application.result.CohortResponse;
import site.omagotchi.learningservice.cohort.domain.Cohort;
import site.omagotchi.learningservice.cohort.domain.CohortErrorCode;
import site.omagotchi.learningservice.cohort.domain.CohortStatus;
import site.omagotchi.learningservice.cohort.infrastructure.CohortMembershipRepository;
import site.omagotchi.learningservice.cohort.infrastructure.CohortRepository;
import site.omagotchi.learningservice.global.auth.GlobalRole;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CohortService {
    private final CohortRepository repository;
    private final CohortMembershipRepository membershipRepository;
    private final CohortAccessService accessService;
    private final CohortEventPublisher eventPublisher;

    /**
     * 새 기수를 PREPARING 상태로 생성한다.
     * 생성자는 검증된 JWT sub와 같은 UUID userId를 createdByUserId에 저장한다.
     */
    @Transactional
    public CohortResponse create(
            CreateCohortCommand command,
            UUID userId,
            GlobalRole globalRole
    ) {
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
    public CohortResponse update(Long cohortId, UpdateCohortCommand command, UUID actorUserId) {
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
    public CohortResponse changeStatus(
            Long cohortId,
            ChangeCohortStatusCommand command,
            GlobalRole globalRole
    ) {
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

            OffsetDateTime closedAt = OffsetDateTime.now();
            cohort.close();

            // 소속 종료를 같은 트랜잭션에 두는 것이 핵심이다. 커밋 직후부터 이 기수 누구도
            // 새 점유·팀·공실 신청을 시작할 수 없어, 뒤따르는 정리가 정지된 대상을 본다.
            // 여기서 멤버십별 이벤트를 내지 않는다 — 팬아웃이 CE-05 순서를 깨뜨린다
            // (CohortMembershipRepository#endActiveByCohortId).
            //
            // 이 벌크 UPDATE는 clearAutomatically라 영속성 컨텍스트를 비운다. 바로 위의
            // close()가 flushAutomatically 덕에 먼저 반영되기에 살아남는 것이지, 그 짝이
            // 빠지면 상태 변경이 조용히 버려진다 — 확인함(CLOSED 기대, ACTIVE 관측).
            membershipRepository.endActiveByCohortId(cohortId, closedAt);

            eventPublisher.publishCohortClosed(new CohortClosedEvent(cohortId, closedAt));
            return CohortResponse.from(cohort);
        }

        throw new BusinessException(CohortErrorCode.INVALID_COHORT_STATUS_TRANSITION);
    }

    private Cohort getCohortOrThrow(Long cohortId) {
        return repository.findById(cohortId)
                .orElseThrow(() -> new BusinessException(CohortErrorCode.COHORT_NOT_FOUND));
    }
}
