package site.omagotchi.learningservice.occupancy.presentation.response;

import site.omagotchi.learningservice.occupancy.application.result.ParticipantCandidateResult;
import site.omagotchi.learningservice.occupancy.application.result.ParticipantCandidateStatus;

import java.util.UUID;

public record ParticipantCandidateResponse(
        UUID userId,
        String displayName,
        String email,
        ParticipantCandidateStatus status
) {
    public static ParticipantCandidateResponse from(ParticipantCandidateResult result) {
        return new ParticipantCandidateResponse(
                result.userId(), result.displayName(), result.email(), result.status());
    }
}
