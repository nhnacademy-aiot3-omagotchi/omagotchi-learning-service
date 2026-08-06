package site.omagotchi.learningservice.cohort.application.command;

import java.time.LocalDate;

/**
 * 기수 기본 정보 수정 명령
 */
public record UpdateCohortCommand(
        String name,
        String description,
        LocalDate startDate,
        LocalDate endDate
) {
}
