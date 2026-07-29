package site.omagotchi.learningservice.study.presentation.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.format.annotation.DateTimeFormat;
import site.omagotchi.learningservice.study.application.command.UpdateStudyRecordCommand;
import site.omagotchi.learningservice.study.application.time.StudyTimePolicy;

import java.time.LocalDate;
import java.time.LocalTime;

public record UpdateStudyRecordRequest(
        @NotNull
        @DateTimeFormat(pattern = "uuuuMMdd")
        LocalDate date,

        @NotNull
        @DateTimeFormat(pattern = "HHmm")
        LocalTime startTime,

        @NotNull
        @DateTimeFormat(pattern = "HHmm")
        LocalTime endTime,

        @NotNull @PositiveOrZero
        Long expectedVersion
) {

    public UpdateStudyRecordCommand toCommand() {
        return new UpdateStudyRecordCommand(
                StudyTimePolicy.toInstant(date, startTime),
                StudyTimePolicy.toInstant(date, endTime),
                expectedVersion
        );
    }
}
