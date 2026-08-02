package site.omagotchi.learningservice.cohort.application.dto.command;

import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;

/**
 * 활성 기수 멤버 역할 변경 명령
 */
public record ChangeCohortMemberRoleCommand(
        CohortMembershipRole role
) {
}
