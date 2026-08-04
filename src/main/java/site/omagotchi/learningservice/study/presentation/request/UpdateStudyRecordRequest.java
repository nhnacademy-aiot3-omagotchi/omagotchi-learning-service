package site.omagotchi.learningservice.study.presentation.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import site.omagotchi.learningservice.study.application.command.UpdateStudyRecordCommand;
import site.omagotchi.learningservice.study.domain.StudyTimePolicy;

import java.time.LocalDate;
import java.time.LocalTime;

public record UpdateStudyRecordRequest(
        @NotNull
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate date,

        @NotNull
        @JsonFormat(pattern = "HH:mm")
        LocalTime startTime,

        @NotNull
        @JsonFormat(pattern = "HH:mm")
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
