package site.omagotchi.learningservice.cohort.presentation.dto.request;

import site.omagotchi.learningservice.cohort.application.dto.command.CreateJoinCommand;

/**
 * 가입 코드 기반 기수 참가 신청 요청
 */
public record CreateJoinRequest(
        String joinCode,
        String code
) {

    public CreateJoinCommand toCommand() {
        return new CreateJoinCommand(resolveJoinCode());
    }

    private String resolveJoinCode() {
        return joinCode != null ? joinCode : code;
    }
}
