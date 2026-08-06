package site.omagotchi.learningservice.cohort.application.command;

import java.util.UUID;

/**
 * 기수 관리자 지정 대상 사용자 명령
 */
public record AssignCohortManagerCommand(
        UUID userId
) {
}
