package site.omagotchi.learningservice.cohort.application.dto.command;

/**
 * 기수 참가 신청 거절 사유 명령
 */
public record RejectMembershipCommand(
        String reason
) {
}
