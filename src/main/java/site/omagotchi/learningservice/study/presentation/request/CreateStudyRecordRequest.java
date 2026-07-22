package site.omagotchi.learningservice.study.presentation.request;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record CreateStudyRecordRequest(
        @NotNull Long cohortId,
        @NotNull Instant startTime,
        @NotNull Instant endTime
) {
}
