package site.omagotchi.learningservice.cohort.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import site.omagotchi.learningservice.cohort.application.dto.command.IssueJoinCodeCommand;

import java.time.OffsetDateTime;

/**
 * 기수 가입 코드 발급 만료 시각 요청
 */
public record IssueJoinCodeRequest(
        @NotNull OffsetDateTime expiresAt
) {

    public IssueJoinCodeCommand toCommand() {
        return new IssueJoinCodeCommand(expiresAt);
    }
}
