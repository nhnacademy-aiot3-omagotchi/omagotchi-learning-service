package site.omagotchi.learningservice.study.presentation.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import site.omagotchi.learningservice.study.application.command.CreateStudyRecordCommand;
import site.omagotchi.learningservice.study.domain.StudyTimePolicy;

import java.time.LocalDate;
import java.time.LocalTime;

public record CreateStudyRecordRequest(
        @NotNull
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate date,

        @NotNull
        @JsonFormat(pattern = "HH:mm")
        LocalTime startTime,

        @NotNull
        @JsonFormat(pattern = "HH:mm")
        LocalTime endTime
) {

    public CreateStudyRecordCommand toCommand() {
        return new CreateStudyRecordCommand(
                StudyTimePolicy.toInstant(date, startTime),
                StudyTimePolicy.toInstant(date, endTime)
        );
    }
}
