package site.omagotchi.learningservice.cohort.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.omagotchi.learningservice.cohort.application.command.ApproveMembershipCommand;
import site.omagotchi.learningservice.cohort.application.command.CreateJoinCommand;
import site.omagotchi.learningservice.cohort.application.command.RejectMembershipCommand;
import site.omagotchi.learningservice.cohort.application.event.CohortMembershipEndedEvent;
import site.omagotchi.learningservice.cohort.application.port.CohortEventPublisher;
import site.omagotchi.learningservice.cohort.application.result.CohortMembershipResponse;
import site.omagotchi.learningservice.cohort.domain.Cohort;
import site.omagotchi.learningservice.cohort.application.CohortErrorCode;
import site.omagotchi.learningservice.cohort.domain.CohortJoinCode;
import site.omagotchi.learningservice.cohort.domain.CohortJoinCodeStatus;
import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;
import site.omagotchi.learningservice.cohort.domain.CohortStatus;
import site.omagotchi.learningservice.cohort.infrastructure.CohortJoinCodeRepository;
import site.omagotchi.learningservice.cohort.infrastructure.CohortMembershipRepository;
import site.omagotchi.learningservice.cohort.infrastructure.CohortRepository;
import site.omagotchi.learningservice.global.auth.GlobalRole;
import site.omagotchi.learningservice.global.exception.BusinessException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CohortMembershipService {

    private static final Set<CohortMembershipStatus> DUPLICATE_TARGET_STATUSES = Set.of(
            CohortMembershipStatus.PENDING,
            CohortMembershipStatus.ACTIVE
    );

    private final CohortRepository cohortRepository;
    private final CohortJoinCodeRepository joinCodeRepository;
    private final CohortMembershipRepository membershipRepository;
    private final CohortAccessService accessService;
    private final CohortEventPublisher eventPublisher;

    /**
     * 기수 소속을 종료한다 (GR-16, MR-26).
     *
     * <p>계정 삭제 훅과 수동 제명이 공유하는 진입점이다. 팀·점유 정리는 여기서 하지 않고
     * {@link CohortMembershipEndedEvent}를 받는 각 파트가 자기 데이터를 정리한다 —
     * 기수 파트가 남의 테이블을 알 이유가 없고, 정리 실패가 종료 자체를 롤백시켜서도
     * 안 되기 때문이다 (ADR space-team/0006).</p>
     *
     * <p><b>멱등하다.</b> 이미 종료됐거나 ACTIVE가 아닌 소속이면 아무것도 하지 않고
     * 이벤트도 내지 않는다. {@code approve}·{@code reject}가 같은 상황에서
     * {@code INVALID_MEMBERSHIP_STATUS_TRANSITION}을 던지는 것과 다른 판단인데,
     * 저쪽은 사용자가 누른 명령이라 "이미 처리됨"을 알려줘야 하지만 여기는 재전달이
     * 전제인 시스템 훅이라 두 번째 도착이 오류가 아니다. 여기서 던지면 훅이 실패로
     * 기록되고 무한 재시도에 빠진다.</p>
     *
     * <p>ACTIVE만 종료 대상인 이유는 {@code CohortMembershipRepository#endActive} 참고 —
     * PENDING 행은 {@code ck_cohort_memberships_processed}에 걸린다.</p>
     *
     * @return 이번 호출로 종료됐으면 {@code true}, 이미 종료 상태였으면 {@code false}
     */
    @Transactional
    public boolean end(Long membershipId) {
        CohortMembership membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new BusinessException(CohortErrorCode.COHORT_MEMBERSHIP_NOT_FOUND));

        OffsetDateTime endedAt = OffsetDateTime.now();
        if (membershipRepository.endActive(membershipId, endedAt) == 0) {
            return false;
        }

        // 발행은 상태 변경 뒤다. 리스너가 AFTER_COMMIT으로 받으므로 실제 정리는 커밋 후이며,
        // 정리 실패가 이 트랜잭션을 롤백시키지 않는다 (ADR-0006).
        eventPublisher.publishMembershipEnded(new CohortMembershipEndedEvent(
                membershipId, membership.getCohortId(), membership.getUserId(), endedAt));
        return true;
    }

    /**
     * 가입 코드로 기수 참가 신청을 생성
     * 같은 userId와 cohortId에 PENDING 또는 ACTIVE 소속이 있으면 기존 소속을 반환해 멱등성을 보장
     * 멱등성: 엘리베이터 5번 누르면 1번 눌러짐 처리
     */
    @Transactional
    public CohortMembershipResponse join(CreateJoinCommand command, UUID userId) {
        String rawJoinCode = command.joinCode();
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
    public List<CohortMembershipResponse> getMyMemberships(UUID userId) {
        return membershipRepository.findByUserIdOrderByRequestedAtDesc(userId).stream()
                .map(CohortMembershipResponse::from)
                .toList();
    }

    /**
     * 특정 기수의 PENDING 참가 신청 목록을 신청순으로 조회
     * 기수 관리자의 승인/거절 대기 목록에서 사용
     */
    public List<CohortMembershipResponse> getPendingJoinRequests(Long cohortId, UUID actorUserId) {
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
    public List<CohortMembershipResponse> getMembers(Long cohortId, UUID actorUserId) {
        accessService.requireManager(cohortId, actorUserId);

        return membershipRepository.findAllByCohortIdOrderByRequestedAtAsc(cohortId).stream()
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
            ApproveMembershipCommand command,
            UUID processedByUserId,
            GlobalRole globalRole
    ) {
        CohortMembership pendingMembership = membershipRepository.findByIdAndStatus(
                membershipId,
                CohortMembershipStatus.PENDING
        ).orElseThrow(() -> new BusinessException(CohortErrorCode.INVALID_MEMBERSHIP_STATUS_TRANSITION));
        validateCohortNotClosed(pendingMembership.getCohortId());
        if (command.role() == CohortMembershipRole.MANAGER) {
            accessService.requireSystemAdmin(globalRole);
        } else {
            accessService.requireManager(pendingMembership.getCohortId(), processedByUserId);
        }

        if (command.role() == CohortMembershipRole.STUDENT
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
                command.role(),
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
    public CohortMembershipResponse reject(
            Long membershipId,
            RejectMembershipCommand command,
            UUID processedByUserId
    ) {
        if (command.reason() == null || command.reason().isBlank()) {
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
                command.reason(),
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

    private CohortMembershipResponse createPendingMembership(Long cohortId, UUID userId) {
        CohortMembership membership = CohortMembership.pending(
                cohortId,
                userId,
                CohortMembershipRole.STUDENT
        );
        return CohortMembershipResponse.from(membershipRepository.save(membership));
    }

    private Optional<CohortMembershipResponse> requestAgainRejectedMembership(Long cohortId, UUID userId) {
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
