package site.omagotchi.learningservice.cohort.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import site.omagotchi.learningservice.cohort.application.dto.command.CreateJoinCommand;

/**
 * 가입 코드 기반 기수 참가 신청 요청
 */
public record CreateJoinRequest(
        @NotBlank String joinCode
) {

    public CreateJoinCommand toCommand() {
        return new CreateJoinCommand(joinCode);
    }
}
