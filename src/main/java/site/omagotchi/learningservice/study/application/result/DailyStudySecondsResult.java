package site.omagotchi.learningservice.study.application.result;

import java.time.LocalDate;

public record DailyStudySecondsResult(
        LocalDate aggregationDate,
        long studySeconds
) {
}
