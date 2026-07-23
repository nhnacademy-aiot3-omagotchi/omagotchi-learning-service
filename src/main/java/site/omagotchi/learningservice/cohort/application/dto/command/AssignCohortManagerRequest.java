package site.omagotchi.learningservice.cohort.application.dto.command;

import jakarta.validation.constraints.NotNull;

/**
 * 기수 관리자 지정 대상 사용자 요청
 */
public record AssignCohortManagerRequest(
        @NotNull Long userId
) {
}
