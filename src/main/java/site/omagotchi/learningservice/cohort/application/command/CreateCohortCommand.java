package site.omagotchi.learningservice.cohort.application.command;

import java.time.LocalDate;

/**
 * 새 기수 생성 기본 정보 명령
 */
public record CreateCohortCommand(
        String name,
        String description,
        LocalDate startDate,
        LocalDate endDate
) {
}
