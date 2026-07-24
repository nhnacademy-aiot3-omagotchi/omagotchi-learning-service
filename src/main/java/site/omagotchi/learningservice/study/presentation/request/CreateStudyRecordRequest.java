package site.omagotchi.learningservice.study.presentation.request;

import jakarta.validation.constraints.NotNull;
import site.omagotchi.learningservice.study.application.command.CreateStudyRecordCommand;

public record CreateStudyRecordRequest(
        @NotNull Long cohortId,
        @NotNull String date,
        @NotNull String startTime,
        @NotNull String endTime
) {

    public CreateStudyRecordCommand toCommand() {
        return new CreateStudyRecordCommand(cohortId, date, startTime, endTime);
    }
}
