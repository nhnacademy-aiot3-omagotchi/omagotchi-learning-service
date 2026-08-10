package site.omagotchi.learningservice.space.presentation.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AssignLabCohortRequest(
        @NotNull
        @Min(1)
        Long cohortId
) {
}
