package site.omagotchi.learningservice.cohort.application.dto.command;

import jakarta.validation.constraints.NotBlank;

/**
 * 가입 코드 기반 기수 참가 신청 요청
 */
public record CreateJoinRequest(
        @NotBlank String joinCode
) {
}
