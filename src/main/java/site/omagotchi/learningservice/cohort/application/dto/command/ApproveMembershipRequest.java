package site.omagotchi.learningservice.cohort.application.dto.command;

import jakarta.validation.constraints.NotNull;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;

/**
 * 기수 참가 신청 승인 역할 요청
 */
public record ApproveMembershipRequest(
        @NotNull CohortMembershipRole role
) {
}
