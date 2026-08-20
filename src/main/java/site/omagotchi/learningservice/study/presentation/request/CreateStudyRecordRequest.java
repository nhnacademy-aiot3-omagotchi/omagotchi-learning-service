package site.omagotchi.learningservice.study.presentation.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import site.omagotchi.learningservice.global.time.AggregationDateTime;
import site.omagotchi.learningservice.study.application.command.CreateStudyRecordCommand;

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
                AggregationDateTime.toInstant(date, startTime),
                AggregationDateTime.toInstant(date, endTime)
        );
    }
}
