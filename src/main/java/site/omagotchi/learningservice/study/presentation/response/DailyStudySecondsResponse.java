package site.omagotchi.learningservice.study.presentation.response;

import site.omagotchi.learningservice.study.application.result.DailyStudySecondsResult;

import java.time.LocalDate;

public record DailyStudySecondsResponse(
        LocalDate aggregationDate,
        long studySeconds
) {

    public static DailyStudySecondsResponse from(DailyStudySecondsResult result) {
        return new DailyStudySecondsResponse(
                result.aggregationDate(),
                result.studySeconds()
        );
    }
}
