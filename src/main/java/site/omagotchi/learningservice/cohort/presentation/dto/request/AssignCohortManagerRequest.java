package site.omagotchi.learningservice.cohort.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import site.omagotchi.learningservice.cohort.application.dto.command.AssignCohortManagerCommand;

/**
 * 기수 관리자 지정 대상 사용자 요청
 */
public record AssignCohortManagerRequest(
        @NotNull Long userId
) {

    public AssignCohortManagerCommand toCommand() {
        return new AssignCohortManagerCommand(userId);
    }
}
