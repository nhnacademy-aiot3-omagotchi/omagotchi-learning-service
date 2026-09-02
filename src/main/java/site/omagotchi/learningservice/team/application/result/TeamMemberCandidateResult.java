package site.omagotchi.learningservice.team.application.result;

import java.util.UUID;

/** 팀 마스터에게 노출하는 같은 기수 팀원 후보. */
public record TeamMemberCandidateResult(
        UUID userId,
        String displayName,
        String email,
        TeamMemberCandidateStatus status
) {
}
