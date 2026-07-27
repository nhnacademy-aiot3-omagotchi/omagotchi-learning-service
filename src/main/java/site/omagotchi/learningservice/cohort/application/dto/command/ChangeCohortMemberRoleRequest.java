package site.omagotchi.learningservice.cohort.application.dto.command;

import jakarta.validation.constraints.NotNull;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;

/**
 * 활성 기수 멤버 역할 변경 요청
 */
public record ChangeCohortMemberRoleRequest(
        @NotNull CohortMembershipRole role
) {
}
