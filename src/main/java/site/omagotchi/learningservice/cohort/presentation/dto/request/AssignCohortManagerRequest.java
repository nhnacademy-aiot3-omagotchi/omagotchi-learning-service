package site.omagotchi.learningservice.cohort.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import site.omagotchi.learningservice.cohort.application.dto.command.AssignCohortManagerCommand;

import java.util.UUID;

/**
 * 기수 관리자 지정 대상 사용자 요청
 */
public record AssignCohortManagerRequest(
        @NotNull UUID userId
) {

    public AssignCohortManagerCommand toCommand() {
        return new AssignCohortManagerCommand(userId);
    }
}
