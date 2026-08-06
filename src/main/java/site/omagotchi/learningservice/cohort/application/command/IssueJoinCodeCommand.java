package site.omagotchi.learningservice.cohort.application.command;

import java.time.OffsetDateTime;

/**
 * 기수 가입 코드 발급 만료 시각 명령
 */
public record IssueJoinCodeCommand(
        OffsetDateTime expiresAt
) {
}
