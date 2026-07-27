package site.omagotchi.learningservice.cohort.application.dto.command;

/**
 * 기수 관리자 지정 대상 사용자 명령
 */
public record AssignCohortManagerCommand(
        Long userId
) {
}
