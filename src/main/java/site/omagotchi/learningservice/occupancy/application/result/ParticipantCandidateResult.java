package site.omagotchi.learningservice.occupancy.application.result;

import java.util.UUID;

public record ParticipantCandidateResult(
        UUID userId,
        String displayName,
        String email,
        ParticipantCandidateStatus status
) {
}
