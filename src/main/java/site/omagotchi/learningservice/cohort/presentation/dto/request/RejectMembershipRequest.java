package site.omagotchi.learningservice.cohort.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import site.omagotchi.learningservice.cohort.application.command.RejectMembershipCommand;

/**
 * 기수 참가 신청 거절 사유 요청
 */
public record RejectMembershipRequest(
        @NotBlank String reason
) {

    public RejectMembershipCommand toCommand() {
        return new RejectMembershipCommand(reason);
    }
}
