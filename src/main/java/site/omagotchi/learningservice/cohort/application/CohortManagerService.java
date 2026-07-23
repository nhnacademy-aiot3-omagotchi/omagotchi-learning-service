package site.omagotchi.learningservice.cohort.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.dto.command.AssignCohortManagerRequest;
import site.omagotchi.learningservice.cohort.application.dto.command.ChangeCohortMemberRoleRequest;
import site.omagotchi.learningservice.cohort.application.dto.result.CohortMembershipResponse;
import site.omagotchi.learningservice.cohort.domain.Cohort;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;
import site.omagotchi.learningservice.cohort.domain.CohortStatus;
import site.omagotchi.learningservice.cohort.infrastructure.CohortMembershipRepository;
import site.omagotchi.learningservice.cohort.infrastructure.CohortRepository;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CohortManagerService {

    private final CohortRepository cohortRepository;
    private final CohortMembershipRepository membershipRepository;
    private final CohortAccessService accessService;

    /**
     * 전역 어드민이 기수 관리자를 명시적으로 지정한다.
     * 기수 생성자는 자동 관리자로 승격하지 않는다.
     */
    @Transactional
    public CohortMembershipResponse assignManager(
            Long cohortId,
            AssignCohortManagerRequest request,
            Long processedByUserId,
            String globalRole
    ) {
        accessService.requireSystemAdmin(globalRole);
        validateCohortCanChangeManager(cohortId);

        return membershipRepository.findByCohortIdAndUserId(cohortId, request.userId())
                .map(membership -> assignExistingMembershipAsManager(membership, processedByUserId))
                .orElseGet(() -> createActiveManager(cohortId, request.userId(), processedByUserId));
    }

    /**
     * ACTIVE 멤버의 역할을 변경한다.
     * 마지막 ACTIVE MANAGER를 다른 역할로 낮추는 작업은 막는다.
     */
    @Transactional
    public CohortMembershipResponse changeMemberRole(
            Long cohortId,
            Long userId,
            ChangeCohortMemberRoleRequest request,
            Long processedByUserId,
            String globalRole
    ) {
        validateCohortCanChangeManager(cohortId);

        CohortMembership membership = membershipRepository.findFirstByCohortIdAndUserIdAndStatusOrderByRequestedAtDesc(
                cohortId,
                userId,
                CohortMembershipStatus.ACTIVE
        ).orElseThrow(() -> new BusinessException(CohortErrorCode.COHORT_MEMBERSHIP_NOT_FOUND));

        if (membership.getRole() == CohortMembershipRole.MANAGER || request.role() == CohortMembershipRole.MANAGER) {
            accessService.requireSystemAdmin(globalRole);
        } else {
            accessService.requireManager(cohortId, processedByUserId);
        }
        // 매니저가 1명 이상이어야 함 (매니저 < 1) 방지
        if (membership.getRole() == CohortMembershipRole.MANAGER
                && request.role() != CohortMembershipRole.MANAGER
                && membershipRepository.countByCohortIdAndRoleAndStatus(
                cohortId,
                CohortMembershipRole.MANAGER,
                CohortMembershipStatus.ACTIVE
        ) <= 1) {
            throw new BusinessException(CohortErrorCode.COHORT_ACTIVE_MANAGER_REQUIRED);
        }

        int updatedCount = membershipRepository.changeActiveRole(
                membership.getId(),
                request.role(),
                OffsetDateTime.now(),
                processedByUserId
        );
        if (updatedCount == 0) {
            throw new BusinessException(CohortErrorCode.INVALID_MEMBERSHIP_STATUS_TRANSITION);
        }

        return getMembershipResponse(membership.getId());
    }

    /**
     * 이미 존재하는 소속 row를 현재 기수의 ACTIVE MANAGER로 만든 뒤 최신 응답을 반환한다.
     * ACTIVE 멤버는 역할만 MANAGER로 변경하고, PENDING 신청은 MANAGER로 승인한다.
     */
    private CohortMembershipResponse assignExistingMembershipAsManager(
            CohortMembership membership,
            Long processedByUserId
    ) {
        if (membership.getStatus() == CohortMembershipStatus.ACTIVE) {
            int updatedCount = membershipRepository.changeActiveRole(
                    membership.getId(),
                    CohortMembershipRole.MANAGER,
                    OffsetDateTime.now(),
                    processedByUserId
            );
            if (updatedCount == 0) {
                throw new BusinessException(CohortErrorCode.INVALID_MEMBERSHIP_STATUS_TRANSITION);
            }
            return getMembershipResponse(membership.getId());
        }

        if (membership.getStatus() == CohortMembershipStatus.PENDING) {
            int updatedCount = membershipRepository.approvePending(
                    membership.getId(),
                    CohortMembershipStatus.ACTIVE,
                    CohortMembershipRole.MANAGER,
                    OffsetDateTime.now(),
                    processedByUserId
            );
            if (updatedCount == 0) {
                throw new BusinessException(CohortErrorCode.INVALID_MEMBERSHIP_STATUS_TRANSITION);
            }
            return getMembershipResponse(membership.getId());
        }

        throw new BusinessException(CohortErrorCode.INVALID_MEMBERSHIP_STATUS_TRANSITION);
    }

    /**
     * 기존 소속 row가 없는 사용자를 현재 기수의 ACTIVE MANAGER로 새로 등록하고 생성 결과를 반환한다.
     */
    private CohortMembershipResponse createActiveManager(Long cohortId, Long userId, Long processedByUserId) {
        CohortMembership membership = CohortMembership.activeManager(cohortId, userId, processedByUserId);
        return CohortMembershipResponse.from(membershipRepository.save(membership));
    }

    /**
     * 관리자 지정/역할 변경이 가능한 기수인지 확인한다.
     * 종료된 기수는 소속 역할을 변경하지 않는다.
     */
    private void validateCohortCanChangeManager(Long cohortId) {
        Cohort cohort = cohortRepository.findById(cohortId)
                .orElseThrow(() -> new BusinessException(CohortErrorCode.COHORT_NOT_FOUND));
        if (cohort.getStatus() == CohortStatus.CLOSED) {
            throw new BusinessException(CohortErrorCode.COHORT_ALREADY_CLOSED);
        }
    }

    /**
     * 조건부 update 이후 영속성 컨텍스트를 비운 repository 결과를 다시 조회해 응답 DTO로 변환한다.
     */
    private CohortMembershipResponse getMembershipResponse(Long membershipId) {
        return membershipRepository.findById(membershipId)
                .map(CohortMembershipResponse::from)
                .orElseThrow(() -> new BusinessException(CohortErrorCode.COHORT_MEMBERSHIP_NOT_FOUND));
    }
}
