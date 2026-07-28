package site.omagotchi.learningservice.study.infrastructure.persistence.repository.projection;

import java.time.LocalDate;

public record DailyStudySeconds(
        LocalDate aggregationDate,
        long studySeconds
) {
}
