package site.omagotchi.learningservice.cohort.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.command.ChangeCohortStatusCommand;
import site.omagotchi.learningservice.cohort.application.command.CreateCohortCommand;
import site.omagotchi.learningservice.cohort.application.command.UpdateCohortCommand;
import site.omagotchi.learningservice.cohort.application.event.CohortClosedEvent;
import site.omagotchi.learningservice.cohort.application.port.CohortEventPublisher;
import site.omagotchi.learningservice.cohort.application.port.CohortActiveLabQuery;
import site.omagotchi.learningservice.cohort.application.port.CohortMembershipQuery;
import site.omagotchi.learningservice.cohort.application.port.CohortPersistence;
import site.omagotchi.learningservice.cohort.application.result.CohortAdminSummaryResult;
import site.omagotchi.learningservice.cohort.application.result.CohortMembershipSummaryResult;
import site.omagotchi.learningservice.cohort.application.result.CohortResponse;
import site.omagotchi.learningservice.cohort.domain.Cohort;
import site.omagotchi.learningservice.cohort.domain.CohortStatus;
import site.omagotchi.learningservice.cohort.infrastructure.CohortMembershipRepository;
import site.omagotchi.learningservice.global.auth.GlobalRole;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CohortService {
    private final CohortPersistence cohortPersistence;
    private final CohortMembershipQuery membershipQuery;
    private final CohortAccessService accessService;
    private final CohortEventPublisher eventPublisher;
    private final CohortManagerAssignmentPolicy managerAssignmentPolicy;
    private final CohortMembershipRepository membershipRepository;
    private final CohortActiveLabQuery cohortActiveLabQuery;
    private final CohortLockService cohortLockService;

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
        Cohort savedCohort = cohortPersistence.save(cohort);
        return CohortResponse.from(savedCohort);
    }

    /**
     * 등록된 모든 기수 목록을 조회한다.
     * 관리자 대시보드의 기수 목록 화면에서 사용한다.
     */
    public List<CohortResponse> getCohorts() {
        return cohortPersistence.findAll().stream()
                .map(CohortResponse::from)
                .toList();
    }

    /**
     * System Admin 전체 기수 화면에 필요한 활성 구성원 수와 관리자 ID를 함께 조회한다.
     */
    public List<CohortAdminSummaryResult> getAdminSummaries(GlobalRole globalRole) {
        accessService.requireSystemAdmin(globalRole);

        Map<Long, CohortMembershipSummaryResult> summariesByCohortId =
                membershipQuery.findAllAdminSummaries().stream()
                        .collect(Collectors.toMap(
                                CohortMembershipSummaryResult::cohortId,
                                Function.identity()
                        ));

        return cohortPersistence.findAll().stream()
                .map(cohort -> {
                    CohortMembershipSummaryResult summary = summariesByCohortId.getOrDefault(
                            cohort.getId(),
                            new CohortMembershipSummaryResult(cohort.getId(), 0L, List.of())
                    );
                    return CohortAdminSummaryResult.from(
                            cohort,
                            summary.memberCount(),
                            summary.managerUserIds()
                    );
                })
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

        managerAssignmentPolicy.acquireCohort(cohortId);
        accessService.requireManager(cohortId, actorUserId);
        Cohort cohort = getCohortOrThrow(cohortId);

        membershipQuery.findAllActiveManagerUserIds(cohortId).stream()
                .sorted()
                .forEach(managerUserId -> managerAssignmentPolicy.validateNoPeriodConflict(
                        managerUserId,
                        cohortId,
                        command.startDate(),
                        command.endDate()
                ));

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

        Cohort cohort = getCohortForUpdateOrThrow(cohortId);

        if (command.status() == CohortStatus.ACTIVE) {
            if (cohort.getStatus() != CohortStatus.PREPARING) {
                throw new BusinessException(CohortErrorCode.INVALID_COHORT_STATUS_TRANSITION);
            }
            if (!membershipQuery.existsActiveManager(cohortId)) {
                throw new BusinessException(CohortErrorCode.COHORT_ACTIVE_MANAGER_REQUIRED);
            }
            if (!cohortActiveLabQuery.existsActiveLab(cohortId)) {
                throw new BusinessException(CohortErrorCode.COHORT_ACTIVE_LAB_REQUIRED);
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

    /**
     * 아직 운영을 시작하지 않은 PREPARING 기수만 삭제한다.
     */
    @Transactional
    public void delete(Long cohortId, GlobalRole globalRole) {
        accessService.requireSystemAdmin(globalRole);

        Cohort cohort = getCohortOrThrow(cohortId);
        if (cohort.getStatus() != CohortStatus.PREPARING) {
            throw new BusinessException(CohortErrorCode.COHORT_DELETE_NOT_ALLOWED);
        }

        cohortPersistence.delete(cohort);
    }

    private Cohort getCohortOrThrow(Long cohortId) {
        return cohortPersistence.findById(cohortId)
                .orElseThrow(() -> new BusinessException(CohortErrorCode.COHORT_NOT_FOUND));
    }

    private Cohort getCohortForUpdateOrThrow(Long cohortId) {
        return cohortLockService.lockCohort(cohortId);
    }
}
