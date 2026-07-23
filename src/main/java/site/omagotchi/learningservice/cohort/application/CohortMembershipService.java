package site.omagotchi.learningservice.cohort.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.dto.command.ApproveMembershipRequest;
import site.omagotchi.learningservice.cohort.application.dto.command.CreateJoinRequest;
import site.omagotchi.learningservice.cohort.application.dto.command.RejectMembershipRequest;
import site.omagotchi.learningservice.cohort.application.dto.result.CohortMembershipResponse;
import site.omagotchi.learningservice.cohort.domain.Cohort;
import site.omagotchi.learningservice.cohort.domain.CohortJoinCode;
import site.omagotchi.learningservice.cohort.domain.CohortJoinCodeStatus;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;
import site.omagotchi.learningservice.cohort.domain.CohortStatus;
import site.omagotchi.learningservice.cohort.infrastructure.CohortJoinCodeRepository;
import site.omagotchi.learningservice.cohort.infrastructure.CohortMembershipRepository;
import site.omagotchi.learningservice.cohort.infrastructure.CohortRepository;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CohortMembershipService {

    private static final Set<CohortMembershipStatus> DUPLICATE_TARGET_STATUSES = Set.of(
            CohortMembershipStatus.PENDING,
            CohortMembershipStatus.ACTIVE
    );

    private final CohortRepository cohortRepository;
    private final CohortJoinCodeRepository joinCodeRepository;
    private final CohortMembershipRepository membershipRepository;
    private final CohortAccessService accessService;

    /**
     * 가입 코드로 기수 참가 신청을 생성
     * 같은 userId와 cohortId에 PENDING 또는 ACTIVE 소속이 있으면 기존 소속을 반환해 멱등성을 보장
     * 멱등성: 엘리베이터 5번 누르면 1번 눌러짐 처리
     */
    @Transactional
    public CohortMembershipResponse join(CreateJoinRequest request, Long userId) {
        String rawJoinCode = request.joinCode();
        if (rawJoinCode == null || rawJoinCode.isBlank()) {
            throw new BusinessException(CohortErrorCode.JOIN_CODE_REQUIRED);
        }

        CohortJoinCode joinCode = joinCodeRepository.findByCodeHash(JoinCodeHash.sha256(rawJoinCode))
                .orElseThrow(() -> new BusinessException(CohortErrorCode.JOIN_CODE_NOT_FOUND));
        validateJoinCode(joinCode);

        Cohort cohort = cohortRepository.findById(joinCode.getCohortId())
                .orElseThrow(() -> new BusinessException(CohortErrorCode.COHORT_NOT_FOUND));
        if (cohort.getStatus() == CohortStatus.CLOSED) {
            throw new BusinessException(CohortErrorCode.COHORT_ALREADY_CLOSED);
        }

        return membershipRepository
                .findFirstByCohortIdAndUserIdAndStatusInOrderByRequestedAtDesc(
                        joinCode.getCohortId(),
                        userId,
                        DUPLICATE_TARGET_STATUSES
                )
                .map(CohortMembershipResponse::from)
                .or(() -> requestAgainRejectedMembership(joinCode.getCohortId(), userId))
                .orElseGet(() -> createPendingMembership(joinCode.getCohortId(), userId));
    }

    /**
     * 사용자의 모든 기수 소속과 참가 신청 상태를 최신 신청순으로 조회
     * 사용자 화면의 내 신청 상태 확인에 사용
     */
    public List<CohortMembershipResponse> getMyMemberships(Long userId) {
        return membershipRepository.findByUserIdOrderByRequestedAtDesc(userId).stream()
                .map(CohortMembershipResponse::from)
                .toList();
    }

    /**
     * 특정 기수의 PENDING 참가 신청 목록을 신청순으로 조회
     * 기수 관리자의 승인/거절 대기 목록에서 사용
     */
    public List<CohortMembershipResponse> getPendingJoinRequests(Long cohortId, Long actorUserId) {
        accessService.requireManager(cohortId, actorUserId);

        return membershipRepository
                .findByCohortIdAndStatusOrderByRequestedAtAsc(cohortId, CohortMembershipStatus.PENDING)
                .stream()
                .map(CohortMembershipResponse::from)
                .toList();
    }

    /**
     * 특정 기수에 생성된 모든 소속 row를 신청순으로 조회
     * 관리자 화면의 소속/역할 관리 목록에서 사용
     */
    public List<CohortMembershipResponse> getMembers(Long cohortId, Long actorUserId) {
        accessService.requireManager(cohortId, actorUserId);

        return membershipRepository.findByCohortIdOrderByRequestedAtAsc(cohortId).stream()
                .map(CohortMembershipResponse::from)
                .toList();
    }

    /**
     * PENDING 참가 신청을 ACTIVE 소속으로 승인
     * 상태 조건부 업데이트를 사용해 이미 처리된 신청의 중복 승인을 방어
     */
    @Transactional
    public CohortMembershipResponse approve(
            Long membershipId,
            ApproveMembershipRequest request,
            Long processedByUserId,
            String globalRole
    ) {
        CohortMembership pendingMembership = membershipRepository.findByIdAndStatus(
                membershipId,
                CohortMembershipStatus.PENDING
        ).orElseThrow(() -> new BusinessException(CohortErrorCode.INVALID_MEMBERSHIP_STATUS_TRANSITION));
        validateCohortNotClosed(pendingMembership.getCohortId());
        if (request.role() == CohortMembershipRole.MANAGER) {
            accessService.requireSystemAdmin(globalRole);
        } else {
            accessService.requireManager(pendingMembership.getCohortId(), processedByUserId);
        }

        if (request.role() == CohortMembershipRole.STUDENT
                && membershipRepository.existsByUserIdAndRoleAndStatusAndEndedAtIsNull(
                pendingMembership.getUserId(),
                CohortMembershipRole.STUDENT,
                CohortMembershipStatus.ACTIVE
        )) {
            throw new BusinessException(CohortErrorCode.COHORT_MEMBERSHIP_DUPLICATED);
        }

        int updatedCount = membershipRepository.approvePending(
                membershipId,
                CohortMembershipStatus.ACTIVE,
                request.role(),
                OffsetDateTime.now(),
                processedByUserId
        );
        if (updatedCount == 0) {
            throw new BusinessException(CohortErrorCode.INVALID_MEMBERSHIP_STATUS_TRANSITION);
        }

        return membershipRepository.findById(membershipId)
                .map(CohortMembershipResponse::from)
                .orElseThrow(() -> new BusinessException(CohortErrorCode.COHORT_MEMBERSHIP_NOT_FOUND));
    }

    /**
     * PENDING 참가 신청을 REJECTED 상태로 거절
     * 거절 사유를 필수로 저장하고, 이미 처리된 신청은 상태 전이 오류로 처리
     */
    @Transactional
    public CohortMembershipResponse reject(Long membershipId, RejectMembershipRequest request, Long processedByUserId) {
        if (request.reason() == null || request.reason().isBlank()) {
            throw new BusinessException(CohortErrorCode.REJECTION_REASON_REQUIRED);
        }

        CohortMembership pendingMembership = membershipRepository.findByIdAndStatus(
                membershipId,
                CohortMembershipStatus.PENDING
        ).orElseThrow(() -> new BusinessException(CohortErrorCode.INVALID_MEMBERSHIP_STATUS_TRANSITION));
        validateCohortNotClosed(pendingMembership.getCohortId());
        accessService.requireManager(pendingMembership.getCohortId(), processedByUserId);

        int updatedCount = membershipRepository.rejectPending(
                membershipId,
                CohortMembershipStatus.REJECTED,
                request.reason(),
                OffsetDateTime.now(),
                processedByUserId
        );
        if (updatedCount == 0) {
            throw new BusinessException(CohortErrorCode.INVALID_MEMBERSHIP_STATUS_TRANSITION);
        }

        return membershipRepository.findById(membershipId)
                .map(CohortMembershipResponse::from)
                .orElseThrow(() -> new BusinessException(CohortErrorCode.COHORT_MEMBERSHIP_NOT_FOUND));
    }

    private CohortMembershipResponse createPendingMembership(Long cohortId, Long userId) {
        CohortMembership membership = CohortMembership.pending(
                cohortId,
                userId,
                CohortMembershipRole.STUDENT
        );
        return CohortMembershipResponse.from(membershipRepository.save(membership));
    }

    private Optional<CohortMembershipResponse> requestAgainRejectedMembership(Long cohortId, Long userId) {
        return membershipRepository.findByCohortIdAndUserId(cohortId, userId)
                .filter(membership -> membership.getStatus() == CohortMembershipStatus.REJECTED)
                .map(membership -> {
                    int updatedCount = membershipRepository.requestAgainRejected(
                            membership.getId(),
                            OffsetDateTime.now()
                    );
                    if (updatedCount == 0) {
                        throw new BusinessException(CohortErrorCode.INVALID_MEMBERSHIP_STATUS_TRANSITION);
                    }
                    return membershipRepository.findById(membership.getId())
                            .map(CohortMembershipResponse::from)
                            .orElseThrow(() -> new BusinessException(CohortErrorCode.COHORT_MEMBERSHIP_NOT_FOUND));
                });
    }

    private void validateCohortNotClosed(Long cohortId) {
        Cohort cohort = cohortRepository.findById(cohortId)
                .orElseThrow(() -> new BusinessException(CohortErrorCode.COHORT_NOT_FOUND));
        if (cohort.getStatus() == CohortStatus.CLOSED) {
            throw new BusinessException(CohortErrorCode.COHORT_ALREADY_CLOSED);
        }
    }

    private void validateJoinCode(CohortJoinCode joinCode) {
        if (joinCode.getStatus() == CohortJoinCodeStatus.REVOKED) {
            throw new BusinessException(CohortErrorCode.JOIN_CODE_REVOKED);
        }
        if (joinCode.getStatus() != CohortJoinCodeStatus.ACTIVE) {
            throw new BusinessException(CohortErrorCode.JOIN_CODE_NOT_FOUND);
        }
        if (!joinCode.isUsableAt(OffsetDateTime.now())) {
            throw new BusinessException(CohortErrorCode.JOIN_CODE_EXPIRED);
        }
    }
}
