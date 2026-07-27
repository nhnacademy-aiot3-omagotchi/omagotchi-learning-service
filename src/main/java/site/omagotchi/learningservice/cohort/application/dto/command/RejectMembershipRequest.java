package site.omagotchi.learningservice.cohort.application.dto.command;

import jakarta.validation.constraints.NotBlank;

/**
 * 기수 참가 신청 거절 사유 요청
 */
public record RejectMembershipRequest(
        @NotBlank String reason
) {
}
