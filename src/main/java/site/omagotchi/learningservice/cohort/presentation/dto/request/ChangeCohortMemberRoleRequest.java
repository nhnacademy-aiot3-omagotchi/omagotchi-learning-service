package site.omagotchi.learningservice.cohort.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import site.omagotchi.learningservice.cohort.application.dto.command.ChangeCohortMemberRoleCommand;
import site.omagotchi.learningservice.cohort.domain.CohortMembershipRole;

/**
 * 활성 기수 멤버 역할 변경 요청
 */
public record ChangeCohortMemberRoleRequest(
        @NotNull CohortMembershipRole role
) {

    public ChangeCohortMemberRoleCommand toCommand() {
        return new ChangeCohortMemberRoleCommand(role);
    }
}
