package site.omagotchi.learningservice.cohort.application.result;

import site.omagotchi.learningservice.cohort.domain.CohortMembership;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 사용자의 기수 소속 또는 참가 신청 상태 조회 결과
 */
public record CohortMembershipResponse(
        Long id,
        Long cohortId,
        UUID userId,
        CohortMembershipRole role,
        CohortMembershipStatus status,
        OffsetDateTime requestedAt,
        OffsetDateTime processedAt,
        UUID processedByUserId,
        String rejectionReason,
        OffsetDateTime endedAt,
        String nickname
) {

    public static CohortMembershipResponse from(CohortMembership membership) {
        return from(membership, null);
    }

    public static CohortMembershipResponse from(CohortMembership membership, String nickname) {
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
                membership.getEndedAt(),
                nickname
        );
    }

    public CohortMembershipResponse withNickname(String nickname) {
        return new CohortMembershipResponse(
                id,
                cohortId,
                userId,
                role,
                status,
                requestedAt,
                processedAt,
                processedByUserId,
                rejectionReason,
                endedAt,
                nickname
        );
    }
}
