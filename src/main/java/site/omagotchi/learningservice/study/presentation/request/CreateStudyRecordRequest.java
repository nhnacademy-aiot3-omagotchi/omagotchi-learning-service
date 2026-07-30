package site.omagotchi.learningservice.study.presentation.request;

import jakarta.validation.constraints.NotNull;
import site.omagotchi.learningservice.study.application.command.CreateStudyRecordCommand;
import site.omagotchi.learningservice.study.domain.StudyTimePolicy;

import java.time.LocalDate;
import java.time.LocalTime;

public record CreateStudyRecordRequest(
        @NotNull
        LocalDate date,

        @NotNull
        LocalTime startTime,

        @NotNull
        LocalTime endTime
) {

    public CreateStudyRecordCommand toCommand() {
        return new CreateStudyRecordCommand(
                StudyTimePolicy.toInstant(date, startTime),
                StudyTimePolicy.toInstant(date, endTime)
        );
    }
}
