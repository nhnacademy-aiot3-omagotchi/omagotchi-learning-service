package site.omagotchi.learningservice.study.presentation.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import site.omagotchi.learningservice.study.application.command.UpdateStudyRecordCommand;

public record UpdateStudyRecordRequest(
        @NotNull String date,
        @NotNull String startTime,
        @NotNull String endTime,
        @NotNull @PositiveOrZero Long expectedVersion
) {

    public UpdateStudyRecordCommand toCommand() {
        return new UpdateStudyRecordCommand(date, startTime, endTime, expectedVersion);
    }
}
