package site.omagotchi.learningservice.study.presentation.request;

import jakarta.validation.constraints.NotNull;
import site.omagotchi.learningservice.study.application.dto.CreateStudyRecordCommand;

public record CreateStudyRecordRequest(
        @NotNull String date,
        @NotNull String startTime,
        @NotNull String endTime
) {

    public CreateStudyRecordCommand toCommand() {
        return new CreateStudyRecordCommand(date, startTime, endTime);
    }
}
