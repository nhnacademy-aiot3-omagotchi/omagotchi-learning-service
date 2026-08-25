package site.omagotchi.learningservice.study.presentation.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import site.omagotchi.learningservice.global.time.AggregationDateTime;
import site.omagotchi.learningservice.study.application.command.CreateStudyRecordCommand;

import java.time.LocalDateTime;

public record CreateStudyRecordRequest(
        @NotNull
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
        LocalDateTime startDateTime,

        @NotNull
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm")
        LocalDateTime endDateTime
) {

    public CreateStudyRecordCommand toCommand() {
        return new CreateStudyRecordCommand(
                AggregationDateTime.toInstant(startDateTime),
                AggregationDateTime.toInstant(endDateTime)
        );
    }
}
