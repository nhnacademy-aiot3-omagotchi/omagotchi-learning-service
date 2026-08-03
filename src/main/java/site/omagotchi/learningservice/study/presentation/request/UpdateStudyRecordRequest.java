package site.omagotchi.learningservice.study.presentation.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.format.annotation.DateTimeFormat;
import site.omagotchi.learningservice.study.application.command.UpdateStudyRecordCommand;
import site.omagotchi.learningservice.study.domain.StudyTimePolicy;

import java.time.LocalDate;
import java.time.LocalTime;

public record UpdateStudyRecordRequest(
        @NotNull
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate date,

        @NotNull
        @DateTimeFormat(pattern = "HH:mm")
        LocalTime startTime,

        @NotNull
        @DateTimeFormat(pattern = "HH:mm")
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
