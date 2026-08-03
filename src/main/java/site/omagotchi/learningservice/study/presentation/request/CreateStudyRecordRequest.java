package site.omagotchi.learningservice.study.presentation.request;

import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import site.omagotchi.learningservice.study.application.command.CreateStudyRecordCommand;
import site.omagotchi.learningservice.study.domain.StudyTimePolicy;

import java.time.LocalDate;
import java.time.LocalTime;

public record CreateStudyRecordRequest(
        @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate date,

        @NotNull
        @DateTimeFormat(pattern = "HH:mm")
        LocalTime startTime,

        @NotNull
        @DateTimeFormat(pattern = "HH:mm")
        LocalTime endTime
) {

    public CreateStudyRecordCommand toCommand() {
        return new CreateStudyRecordCommand(
                StudyTimePolicy.toInstant(date, startTime),
                StudyTimePolicy.toInstant(date, endTime)
        );
    }
}
