package site.omagotchi.learningservice.cohort.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import site.omagotchi.learningservice.cohort.application.dto.command.CreateCohortCommand;

import java.time.LocalDate;

/**
 * 새 기수 생성 기본 정보 요청
 */
public record CreateCohortRequest(
        @NotBlank String name,
        String description,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate
) {

    public CreateCohortCommand toCommand() {
        return new CreateCohortCommand(name, description, startDate, endDate);
    }
}
