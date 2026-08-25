package site.omagotchi.learningservice.study.presentation.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import site.omagotchi.learningservice.global.time.AggregationDateTime;
import site.omagotchi.learningservice.study.application.command.UpdateStudyRecordCommand;

import java.time.LocalDateTime;

public record UpdateStudyRecordRequest(
        @NotNull
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
        LocalDateTime startDateTime,

        @NotNull
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
        LocalDateTime endDateTime,

        @NotNull @PositiveOrZero
        Long expectedVersion
) {

    public UpdateStudyRecordCommand toCommand() {
        return new UpdateStudyRecordCommand(
                AggregationDateTime.toInstant(startDateTime),
                AggregationDateTime.toInstant(endDateTime),
                expectedVersion
        );
    }
}
