package site.omagotchi.learningservice.cohort.application.dto.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

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
}
