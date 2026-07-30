package site.omagotchi.learningservice.cohort.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import site.omagotchi.learningservice.cohort.application.dto.command.UpdateCohortCommand;

import java.time.LocalDate;

/**
 * 기수 기본 정보 수정 요청
 */
public record UpdateCohortRequest(
        @NotBlank String name,
        String description,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate
) {

    public UpdateCohortCommand toCommand() {
        return new UpdateCohortCommand(name, description, startDate, endDate);
    }
}
