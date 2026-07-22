package site.omagotchi.learningservice.study.presentation.request;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record UpdateStudyRecordRequest(
        @NotNull Instant startTime,
        @NotNull Instant endTime,
        @NotNull Long expectedVersion
) {
}
