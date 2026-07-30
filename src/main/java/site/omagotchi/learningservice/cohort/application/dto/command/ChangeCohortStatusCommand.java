package site.omagotchi.learningservice.cohort.application.dto.command;

import site.omagotchi.learningservice.cohort.domain.CohortStatus;

/**
 * 기수 운영 상태 변경 명령
 */
public record ChangeCohortStatusCommand(
        CohortStatus status
) {
}
