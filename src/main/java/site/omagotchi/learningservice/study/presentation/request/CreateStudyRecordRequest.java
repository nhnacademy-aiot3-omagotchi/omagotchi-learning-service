package site.omagotchi.learningservice.study.presentation.request;

import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import site.omagotchi.learningservice.study.application.command.CreateStudyRecordCommand;
import site.omagotchi.learningservice.study.application.time.StudyTimePolicy;

import java.time.LocalDate;
import java.time.LocalTime;

public record CreateStudyRecordRequest(
        @NotNull
        @DateTimeFormat(pattern = "uuuuMMdd")
        LocalDate date,

        @NotNull
        @DateTimeFormat(pattern = "HHmm")
        LocalTime startTime,

        @NotNull
        @DateTimeFormat(pattern = "HHmm")
        LocalTime endTime
) {

    public CreateStudyRecordCommand toCommand() {
        return new CreateStudyRecordCommand(
                StudyTimePolicy.toInstant(date, startTime),
                StudyTimePolicy.toInstant(date, endTime)
        );
    }
}
