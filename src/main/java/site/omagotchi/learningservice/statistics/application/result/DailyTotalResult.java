package site.omagotchi.learningservice.statistics.application.result;

import java.time.LocalDate;

public record DailyTotalResult(
        LocalDate aggregationDate,
        long studySeconds
) {
}
