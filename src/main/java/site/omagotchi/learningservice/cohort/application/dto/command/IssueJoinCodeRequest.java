package site.omagotchi.learningservice.cohort.application.dto.command;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

/**
 * 기수 가입 코드 발급 만료 시각 요청
 */
public record IssueJoinCodeRequest(
        @NotNull OffsetDateTime expiresAt
) {
}
