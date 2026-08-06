package site.omagotchi.learningservice.cohort.application.command;

/**
 * 가입 코드 기반 기수 참가 신청 명령
 */
public record CreateJoinCommand(
        String joinCode
) {
}
