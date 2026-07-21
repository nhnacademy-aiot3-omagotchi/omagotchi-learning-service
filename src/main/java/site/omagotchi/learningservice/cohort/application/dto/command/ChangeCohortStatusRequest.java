package site.omagotchi.learningservice.cohort.application.dto.command;

import jakarta.validation.constraints.NotNull;
import site.omagotchi.learningservice.cohort.domain.CohortStatus;

/**
 * 기수 운영 상태 변경 요청
 */
public record ChangeCohortStatusRequest(
        @NotNull CohortStatus status
) {
}
