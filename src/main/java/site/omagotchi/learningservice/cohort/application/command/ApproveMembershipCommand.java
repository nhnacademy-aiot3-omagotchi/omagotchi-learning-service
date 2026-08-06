package site.omagotchi.learningservice.cohort.application.command;

import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;

/**
 * 기수 참가 신청 승인 역할 명령
 */
public record ApproveMembershipCommand(
        CohortMembershipRole role
) {
}
