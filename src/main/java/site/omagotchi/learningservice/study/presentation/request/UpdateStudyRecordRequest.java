package site.omagotchi.learningservice.study.presentation.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import site.omagotchi.learningservice.global.time.AggregationDateTime;
import site.omagotchi.learningservice.study.application.command.UpdateStudyRecordCommand;

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
                AggregationDateTime.toInstant(date, startTime),
                AggregationDateTime.toInstant(date, endTime),
                expectedVersion
        );
    }
}
