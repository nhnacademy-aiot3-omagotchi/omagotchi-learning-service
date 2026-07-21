package site.omagotchi.learningservice.cohort.application.dto.result;

import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;

import java.time.OffsetDateTime;

/**
 * 사용자의 기수 소속 또는 참가 신청 상태 조회 결과
 */
public record CohortMembershipResponse(
        Long id,
        Long cohortId,
        Long userId,
        CohortMembershipRole role,
        CohortMembershipStatus status,
        OffsetDateTime requestedAt,
        OffsetDateTime processedAt,
        Long processedByUserId,
        String rejectionReason,
        OffsetDateTime endedAt
) {

    public static CohortMembershipResponse from(CohortMembership membership) {
        return new CohortMembershipResponse(
                membership.getId(),
                membership.getCohortId(),
                membership.getUserId(),
                membership.getRole(),
                membership.getStatus(),
                membership.getRequestedAt(),
                membership.getProcessedAt(),
                membership.getProcessedByUserId(),
                membership.getRejectionReason(),
                membership.getEndedAt()
        );
    }
}
